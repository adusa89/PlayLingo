package cz.playlingo.core

/**
 * Typické herní repliky pro zkoušku překladu. Zkouška v emulátoru je nechá přeložit stejnou
 * cestou jako titulky ze hry a výsledek uloží do `preklad.txt` – podle něj se ladí, aby překlad
 * zněl přirozeně (viz [Natural]). Věty jsou vybrané tak, aby pokryly oslovení jedné osoby
 * i více lidí, rozkazy, otázky a běžné obraty, na kterých se strojový překlad nejčastěji splete.
 */
object TranslationProbe {
    val LINES: List<String> = listOf(
        // otázky na jednu osobu
        "What are you doing here?", "Are you sure about this?", "Do you know who I am?", "Can you help me?",
        "What do you want?", "Where did you get that?", "Did you hear that?", "Are you out of your mind?",
        "How much do you want for it?", "Have you seen my brother?", "Why didn't you tell me?", "Will you come with me?",
        // rozkazy a výzvy
        "Get on your horse.", "Follow me.", "Stay close.", "Hurry up!", "Watch out!", "Get down!", "Take cover!",
        "Hold on.", "Hang on a second.", "Wait for me.", "Leave me alone.", "Shut up.", "Calm down.", "Take it easy.",
        "Give me your gun.", "Put your hands up!", "Drop your weapon!", "Don't move!", "Open the door.", "Give it back.",
        "Tell me the truth.", "Trust me.", "Be careful.", "Look at me.", "Come here.", "Go away.", "Let him go.",
        "Keep your voice down, they can hear us.", "Hand me the lantern and stay close.", "Run for the gate!",
        // běžné obraty
        "I don't know.", "No idea.", "I have no idea what you're talking about.", "Come on, let's go!", "Never mind.",
        "Forget it.", "It doesn't matter.", "I'll take care of it.", "You can count on me.", "I owe you one.",
        "Thanks for your help.", "You're welcome.", "What's going on?", "What happened?", "Where are we going?",
        "I'm going to kill you.", "You'll pay for this.", "I don't trust him.", "He's lying.", "That's not true.",
        "You're right.", "You were right about him.", "I was wrong.", "I'm sorry.", "It's not your fault.",
        "We have to get out of here.", "We're running out of time.", "There's no time to explain.",
        "I've got a bad feeling about this.", "Something's wrong.", "Good job.", "Well done.", "Nice shot!",
        "See you later.", "Take care of yourself.", "I need a drink.", "Let's get this over with.", "That's enough!",
        "Mind your own business.", "What the hell is wrong with you?", "You've got to be kidding me.",
        "I can't believe it.", "It's a long story.", "We had a deal.", "I've been looking for you.",
        "You shouldn't have come here.", "They're coming!", "He got away.", "I think we lost them.",
        "The bridge is out, we have to swim.", "If we keep going like this, they will find us.",
        "I don't think anyone has been here for years.", "Get on your horse, we ride at dawn.",
        // oslovení více lidí (tykání se tu měnit nesmí)
        "You two, come with me.", "All of you, get out!", "You and your friends should leave.",
        "Boys, get on your horses.", "Are you all ready?", "You guys wait here.",
        // minulý čas a podmínka
        "What did you do?", "Did you see that?", "You lied to me.", "You could have told me.", "Would you help me?",
        "If you had listened to me, we would be rich.", "You said you would come.", "Where have you been?",
    )
}
