# ⌚ Band Trigger ⚡ (v1.0.0)

[![Language: English](https://img.shields.io/badge/Language-English-blue.svg)](#-english-version)
[![Idioma: Português](https://img.shields.io/badge/Idioma-Portugu%C3%AAs-green.svg)](#-versão-em-português)

---

## 🇺🇸 English Version

**Band Trigger** is an Android application developed to integrate smartbands (like the Galaxy Fit 3) with smart home devices and general automations (lights, PCs, gates, etc.). 

Since most smartbands only communicate with the phone via media controls, this app runs in the background pretending to be a music player. It intercepts the watch's Play/Pause commands and turns them into automation triggers (Webhooks) to control your home remotely. [Sequematic](https://sequematic.com/) acts as the intermediary, making the automation completely free.

### 🏍️ Origin Story
I work the night shift and commute by motorcycle. I always arrive home at 5:15 AM (still dark). My phone is usually tucked away at the bottom of my backpack, either because of the rain or for safety during the ride. 

When I get home, there's the whole process of taking off the helmet, gloves, and, on rainy days, the raincoat. This was bothering me because I didn't have enough hands to grab my phone and turn on the bedroom light, and I don't like using voice commands in the middle of the night. 

That's when I thought: *"The only communication my smartband has with my phone is through media controls. What if I try to intercept these commands to trigger other things?"* And that's how the idea of using my wrist as a universal remote control was born.

### 🛠️ How it Works
The app creates a "ghost music player" (using Android's `MediaSession` class). The watch thinks it's controlling the phone's media, but the app intercepts the click and triggers an HTTP request.
* **Play / Pause:** Triggers the main Webhook (e.g., Turn on/off light, Turn on PC).
* **Next / Previous:** *(Planned for future updates)*.

### 🎯 Who is it for?
* Those who want to control automations from their wrist without spending money on expensive smartwatches.
* Those who prefer silent commands over voice assistants in the middle of the night.
* Those whose phones are often inaccessible (in a backpack, riding a motorcycle, etc.) and need a quick shortcut.

### ⚙️ Setup & Usage
1. **Generate your Webhook URLs:** Create a free account on [Sequematic](https://sequematic.com/) and link your smart services. Create sequences using the **Custom Webhook** trigger.
2. **Configure the App:** Install the `.apk` on your Android device. Open **Band Trigger**, paste your 'Turn On' and 'Turn Off' links, and click **Save**.
3. **Control:** Open the media controller on your smartband and press **Play/Pause** to trigger the webhook.

### 💻 Tech Stack
* **Language:** Kotlin
* **IDE:** Android Studio
* **Integrations:** Webhooks (`HTTP GET requests`), Sequematic / Smart Life APIs.
* **Core Components:** `MediaSession`, `Service`, `SharedPreferences`, `HttpURLConnection`.

---
---

## 🇧🇷 Versão em Português

O **Band Trigger** é um aplicativo Android desenvolvido para integrar smartbands (como o Galaxy Fit 3) com dispositivos de casa inteligente e automações em geral (lâmpadas, computadores, portões, etc). 

Como a maioria das smartbands só se comunica com o celular através do controle de mídia, este app roda em segundo plano fingindo ser um player de música. Ele intercepta os comandos de Play/Pause do relógio e os transforma em gatilhos de automação (Webhooks) para controlar a sua casa à distância. Quem faz essa intermediação é o [Sequematic](https://sequematic.com/), tornando a automação totalmente gratuita.


### 🏍️ A Origem do Projeto
Eu trabalho no turno da madrugada e utilizo uma moto para locomoção. Sempre chego às 05:15 da manhã (ainda de noite). Quase sempre meu celular vem guardado no fundo da mochila, seja por causa da chuva ou por segurança no trajeto.

Ao chegar em casa, há todo o processo de tirar o capacete, luvas e, em dias de chuva, a capa. Isso estava me incomodando, porque não tenho mãos suficientes para pegar o celular e ligar a lâmpada do quarto, e não gosto de usar comandos de voz de madrugada.

Foi então que pensei: *"A única comunicação que minha smartband tem com o celular é pelo controle de mídia. E se eu tentar interceptar esses comandos para disparar outras coisas?"* Assim nasceu a ideia de usar o pulso como um controle remoto universal.

### 🛠️ Como funciona a "gambiarra" inteligente
O app cria um "player de música fantasma" (usando a classe `MediaSession` do Android). O relógio entende que está controlando a mídia do celular, mas o aplicativo intercepta o clique e dispara uma requisição HTTP.
* **Play / Pause:** Dispara o Webhook principal (ex: Ligar/Desligar luz, Ligar PC).
* **Avançar / Voltar:** *(Planejado para futuras atualizações)*.

### 🎯 Pra quem é útil?
* Quem quer controlar automações pelo pulso sem gastar com smartwatches caros.
* Quem prefere comandos silenciosos em vez de assistentes de voz de madrugada.
* Quem costuma estar com o celular inacessível (mochila, pilotando, etc) e precisa de um atalho rápido.

### ⚙️ Como configurar e usar
1. **Gere as suas URLs de Webhook:** Crie uma conta no [Sequematic](https://sequematic.com/) e vincule aos seus serviços. Crie sequências usando o gatilho **Custom Webhook** para gerar os links únicos.
2. **Configure o App:** Instale o arquivo `.apk` no celular. Abra o **Band Trigger**, cole o link de Ligar no primeiro campo e o de Desligar no segundo. Clique em **Salvar Configurações**.
3. **Controle o Dispositivo:** Abra o controlador de mídia na sua smartband. Aperte o botão de **Play/Pause** para disparar o webhook.

### 💻 Tecnologias Utilizadas
* **Linguagem:** Kotlin
* **IDE:** Android Studio
* **Integrações:** Webhooks (`HTTP GET requests`), APIs de terceiros (Sequematic / Smart Life).
* **Componentes Android:** `MediaSession`, `Service`, `SharedPreferences`, `HttpURLConnection`.
