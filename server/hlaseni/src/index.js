// PlayLingo – schránka na hlášení a snímky od testerů (Cloudflare Worker + úložiště R2).
//
// Tester v aplikaci zvolí „Poslat vývojáři“: aplikace pošle ZIP (hlášení o stavu, případně dávku snímků ze hry)
// a dostane kód, např. K7M2-QX9P. Majitel ho předá Claudovi, ten balík stáhne čtecím klíčem.
//
//   POST   /v1/nahrat          ZIP v těle, bez přihlášení → 201 {"kod":"K7M2QX9P","platnost_dni":30}
//   GET    /v1/seznam          Authorization: Bearer <CTENI_KLIC> → seznam balíků (nejnovější první)
//   GET    /v1/hlaseni/<kod>   Authorization: Bearer <CTENI_KLIC> → ZIP
//   DELETE /v1/hlaseni/<kod>   Authorization: Bearer <CTENI_KLIC>
//   GET    /v1/stav            → {"ok":true}
//
// Nahrávat může kdokoli s aplikací (klíč v APK by stejně nebyl tajný), proto jsou tu jen meze: nejvýš MAX_MB na balík,
// HODINA_IP odeslání za hodinu z jedné adresy, DEN_POCET balíků a DEN_MB megabajtů za den celkem; balíky se po
// PLATNOST_DNI dnech samy mažou (denní úklid v `scheduled`). Číst, mazat a vypsat smí jen ten, kdo má CTENI_KLIC
// (tajný údaj Workeru, v repozitáři není). Obsah balíků se tu neotvírá – aplikace do nich klíče ani čísla účtů nedává.

export const MAX_MB = 45;
export const HODINA_IP = 6;
export const DEN_POCET = 300;
export const DEN_MB = 3000;
export const PLATNOST_DNI = 30;
// bez 0/O, 1/I/L – kód se opisuje
export const ABECEDA = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";

const DRUHY = ["hlaseni", "snimky"];

export function novyKod(nahodne = (n) => crypto.getRandomValues(new Uint8Array(n))) {
  const b = nahodne(8);
  let s = "";
  for (let i = 0; i < 8; i++) s += ABECEDA[b[i] % ABECEDA.length];
  return s;
}

/** Kód z adresy nebo od člověka („k7m2-qx9p“) → „K7M2QX9P“, neplatný → null. */
export function cistyKod(s) {
  const k = String(s || "").toUpperCase().replace(/[-\s]/g, "");
  if (k.length !== 8) return null;
  for (const c of k) if (!ABECEDA.includes(c)) return null;
  return k;
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });
}

/** Porovnání klíče bez prozrazení délky shody časem. */
export function stejnyKlic(a, b) {
  if (typeof a !== "string" || typeof b !== "string" || !b) return false;
  let rozdil = a.length ^ b.length;
  for (let i = 0; i < Math.max(a.length, b.length); i++) {
    rozdil |= (a.charCodeAt(i) || 0) ^ (b.charCodeAt(i) || 0);
  }
  return rozdil === 0;
}

function smiCist(request, env) {
  const h = request.headers.get("authorization") || "";
  return h.startsWith("Bearer ") && stejnyKlic(h.slice(7).trim(), env.CTENI_KLIC);
}

function den(ms) {
  return new Date(ms).toISOString().slice(0, 10);
}

/** Počítadlo v R2 (malý JSON objekt). Souběžné zápisy se můžou o kus minout – pro meze to stačí. */
async function pricti(env, klic, o) {
  const obj = await env.HLASENI.get(klic);
  const stav = obj ? await obj.json() : { pocet: 0, bajty: 0 };
  stav.pocet += o.pocet;
  stav.bajty += o.bajty;
  await env.HLASENI.put(klic, JSON.stringify(stav));
  return stav;
}

async function precti(env, klic) {
  const obj = await env.HLASENI.get(klic);
  return obj ? await obj.json() : { pocet: 0, bajty: 0 };
}

async function nahrat(request, env, ted) {
  const delka = Number(request.headers.get("content-length") || "0");
  if (!delka) return json({ chyba: "prazdne" }, 400);
  if (delka > MAX_MB * 1024 * 1024) return json({ chyba: "velke", maxMB: MAX_MB }, 413);

  const ip = request.headers.get("cf-connecting-ip") || "?";
  const hodina = new Date(ted).toISOString().slice(0, 13);
  const klicIp = `pocty/ip/${hodina}/${ip}`;
  if ((await precti(env, klicIp)).pocet >= HODINA_IP) return json({ chyba: "moc" }, 429);
  const klicDen = `pocty/den/${den(ted)}`;
  const dnes = await precti(env, klicDen);
  if (dnes.pocet >= DEN_POCET || dnes.bajty + delka > DEN_MB * 1024 * 1024) return json({ chyba: "limit" }, 503);

  const telo = new Uint8Array(await request.arrayBuffer());
  if (telo.length > MAX_MB * 1024 * 1024) return json({ chyba: "velke", maxMB: MAX_MB }, 413);
  // ZIP začíná „PK\3\4“ (prázdný ZIP „PK\5\6“ nic nenese)
  if (telo.length < 4 || telo[0] !== 0x50 || telo[1] !== 0x4b || telo[2] !== 3 || telo[3] !== 4) {
    return json({ chyba: "zip" }, 400);
  }

  let kod = novyKod();
  for (let i = 0; i < 3 && (await env.HLASENI.head(`h/${kod}.zip`)); i++) kod = novyKod();

  const druh = DRUHY.includes(request.headers.get("x-playlingo-druh")) ? request.headers.get("x-playlingo-druh") : "hlaseni";
  const verze = (request.headers.get("x-playlingo-verze") || "").replace(/[^0-9.]/g, "").slice(0, 12);
  await env.HLASENI.put(`h/${kod}.zip`, telo, {
    httpMetadata: { contentType: "application/zip" },
    customMetadata: { druh, verze, cas: new Date(ted).toISOString() },
  });
  await pricti(env, klicIp, { pocet: 1, bajty: telo.length });
  await pricti(env, klicDen, { pocet: 1, bajty: telo.length });
  return json({ kod, platnost_dni: PLATNOST_DNI }, 201);
}

async function seznam(env) {
  const vse = [];
  let kurzor;
  do {
    const r = await env.HLASENI.list({ prefix: "h/", cursor: kurzor, include: ["customMetadata"] });
    for (const o of r.objects) {
      vse.push({
        kod: o.key.slice(2, -4),
        cas: o.customMetadata?.cas || new Date(o.uploaded).toISOString(),
        velikost: o.size,
        druh: o.customMetadata?.druh || "",
        verze: o.customMetadata?.verze || "",
      });
    }
    kurzor = r.truncated ? r.cursor : undefined;
  } while (kurzor);
  vse.sort((a, b) => (a.cas < b.cas ? 1 : -1));
  return vse;
}

/** Denní úklid: balíky starší než PLATNOST_DNI a počítadla starší než 2 dny. */
export async function uklid(env, ted) {
  const hranice = ted - PLATNOST_DNI * 24 * 3600 * 1000;
  const pocitadla = ted - 2 * 24 * 3600 * 1000;
  let smazano = 0;
  for (const prefix of ["h/", "pocty/"]) {
    let kurzor;
    do {
      const r = await env.HLASENI.list({ prefix, cursor: kurzor });
      const pryc = r.objects
        .filter((o) => new Date(o.uploaded).getTime() < (prefix === "h/" ? hranice : pocitadla))
        .map((o) => o.key);
      if (pryc.length) await env.HLASENI.delete(pryc);
      smazano += pryc.length;
      kurzor = r.truncated ? r.cursor : undefined;
    } while (kurzor);
  }
  return smazano;
}

export async function obsluz(request, env, ted = Date.now()) {
  const url = new URL(request.url);
  const cesta = url.pathname.replace(/\/+$/, "");
  try {
    if (cesta === "/v1/stav" && request.method === "GET") return json({ ok: true, maxMB: MAX_MB });
    if (cesta === "/v1/nahrat" && request.method === "POST") return await nahrat(request, env, ted);

    if (cesta === "/v1/seznam" || cesta.startsWith("/v1/hlaseni/")) {
      if (!smiCist(request, env)) return json({ chyba: "klic" }, 401);
      if (cesta === "/v1/seznam" && request.method === "GET") return json(await seznam(env));
      const kod = cistyKod(cesta.slice("/v1/hlaseni/".length));
      if (!kod) return json({ chyba: "kod" }, 400);
      if (request.method === "GET") {
        const obj = await env.HLASENI.get(`h/${kod}.zip`);
        if (!obj) return json({ chyba: "neni" }, 404);
        return new Response(obj.body, {
          headers: {
            "content-type": "application/zip",
            "content-disposition": `attachment; filename="playlingo-${kod}.zip"`,
            "cache-control": "no-store",
          },
        });
      }
      if (request.method === "DELETE") {
        await env.HLASENI.delete(`h/${kod}.zip`);
        return json({ ok: true });
      }
    }
    return json({ chyba: "cesta" }, 404);
  } catch (e) {
    // podrobnosti jen do záznamu Workeru, ven nic
    console.log("chyba", e && e.stack);
    return json({ chyba: "server" }, 500);
  }
}

export default {
  fetch: (request, env) => obsluz(request, env),
  scheduled: (_udalost, env, ctx) => ctx.waitUntil(uklid(env, Date.now())),
};
