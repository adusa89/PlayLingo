// Zkouška schránky na hlášení bez Cloudflare: R2 nahrazuje objekt v paměti.  node --test test/server.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import { obsluz, uklid, novyKod, cistyKod, stejnyKlic, ABECEDA, HODINA_IP, MAX_MB } from "../src/index.js";

class PametR2 {
  constructor() { this.o = new Map(); }
  async put(key, value, opts = {}) {
    const data = typeof value === "string" ? new TextEncoder().encode(value) : new Uint8Array(value);
    this.o.set(key, { data, uploaded: new Date(this.ted ?? Date.now()), customMetadata: opts.customMetadata || {} });
  }
  async get(key) {
    const x = this.o.get(key);
    if (!x) return null;
    return {
      body: x.data,
      json: async () => JSON.parse(new TextDecoder().decode(x.data)),
    };
  }
  async head(key) { return this.o.has(key) ? {} : null; }
  async delete(keys) { for (const k of [].concat(keys)) this.o.delete(k); }
  async list({ prefix }) {
    const objects = [...this.o.entries()].filter(([k]) => k.startsWith(prefix))
      .map(([key, x]) => ({ key, size: x.data.length, uploaded: x.uploaded, customMetadata: x.customMetadata }));
    return { objects, truncated: false };
  }
}

const KLIC = "tajny-klic-pro-zkousku";
const zip = (n = 100) => { const b = new Uint8Array(n); b.set([0x50, 0x4b, 3, 4]); return b; };
const prostredi = () => ({ HLASENI: new PametR2(), CTENI_KLIC: KLIC });
const nahrat = (env, telo, ip = "1.2.3.4", ted) => obsluz(new Request("https://x/v1/nahrat", {
  method: "POST", body: telo,
  headers: { "content-length": String(telo.length), "cf-connecting-ip": ip, "x-playlingo-druh": "snimky", "x-playlingo-verze": "165" },
}), env, ted);
const cist = (env, cesta, klic = KLIC, metoda = "GET") =>
  obsluz(new Request("https://x" + cesta, { method: metoda, headers: klic ? { authorization: "Bearer " + klic } : {} }), env);

test("kód: 8 znaků bez zaměnitelných, s pomlčkou i malými písmeny se přečte", () => {
  const k = novyKod();
  assert.equal(k.length, 8);
  for (const c of k) assert.ok(ABECEDA.includes(c));
  assert.equal(cistyKod("k7m2-qx9p"), "K7M2QX9P");
  assert.equal(cistyKod("K7M2QX9O"), null);      // O v abecedě není
  assert.equal(cistyKod("../../x"), null);
});

test("nahrát ZIP → kód; stáhnout jen s klíčem", async () => {
  const env = prostredi();
  const r = await nahrat(env, zip());
  assert.equal(r.status, 201);
  const { kod, platnost_dni } = await r.json();
  assert.equal(platnost_dni, 30);
  assert.equal((await cist(env, "/v1/hlaseni/" + kod, null)).status, 401);
  assert.equal((await cist(env, "/v1/hlaseni/" + kod, "spatny")).status, 401);
  const z = await cist(env, "/v1/hlaseni/" + kod.slice(0, 4) + "-" + kod.slice(4));
  assert.equal(z.status, 200);
  assert.equal(z.headers.get("content-type"), "application/zip");
  const s = await (await cist(env, "/v1/seznam")).json();
  assert.equal(s.length, 1);
  assert.equal(s[0].druh, "snimky");
  assert.equal(s[0].verze, "165");
  assert.equal((await cist(env, "/v1/hlaseni/" + kod, KLIC, "DELETE")).status, 200);
  assert.equal((await cist(env, "/v1/hlaseni/" + kod)).status, 404);
});

test("jen ZIP, ne přes limit, nejvýš pár odeslání za hodinu z jedné adresy", async () => {
  const env = prostredi();
  assert.equal((await nahrat(env, new TextEncoder().encode("ahoj"))).status, 400);
  const velke = new Request("https://x/v1/nahrat", { method: "POST", body: zip(10),
    headers: { "content-length": String(MAX_MB * 1024 * 1024 + 1) } });
  assert.equal((await obsluz(velke, env)).status, 413);
  for (let i = 0; i < HODINA_IP; i++) assert.equal((await nahrat(env, zip(), "5.5.5.5")).status, 201);
  assert.equal((await nahrat(env, zip(), "5.5.5.5")).status, 429);
  assert.equal((await nahrat(env, zip(), "6.6.6.6")).status, 201);
});

test("úklid smaže balíky po 30 dnech", async () => {
  const env = prostredi();
  const den = 24 * 3600 * 1000;
  const ted = Date.now();
  env.HLASENI.ted = ted - 31 * den;
  await nahrat(env, zip(), "1.1.1.1", ted - 31 * den);
  env.HLASENI.ted = ted;
  const r = await nahrat(env, zip(), "1.1.1.1", ted);
  const { kod } = await r.json();
  await uklid(env, ted);
  const s = await (await cist(env, "/v1/seznam")).json();
  assert.deepEqual(s.map((x) => x.kod), [kod]);
});

test("klíč se porovná celý", () => {
  assert.ok(stejnyKlic("abc", "abc"));
  assert.ok(!stejnyKlic("abc", "abd"));
  assert.ok(!stejnyKlic("ab", "abc"));
  assert.ok(!stejnyKlic("abc", undefined));
});
