# 🚀 Band Trigger (v1.4.1)

🌎 *Choose your language / Escolha seu idioma:* 

[![English](https://img.shields.io/badge/English-blue?style=for-the-badge)](#-english)
[![Português](https://img.shields.io/badge/Portugu%C3%AAs-green?style=for-the-badge)](#-português)

---

## 🇺🇸 English

### 💡 *"Smartbands were designed to track steps and skip songs. I decided they could do more."*

Band Trigger is an Android application built to give a completely new purpose to your wearable device. It intercepts media control events from your smartband and translates them into custom HTTP requests and hardware commands, effectively turning your simple fitness band into a powerful smart home and device remote.

---

### ✨ Features
- **Smart Home Webhooks:** Control smart lights, plugs, or IFTTT routines.
- **PC Media Controller:** Play, pause, or skip media on your desktop using the provided standalone Python server.
- **Smart Toggle Display:** Real-time feedback on your wrist! The app updates the track title to show `[ ON ]` or `[ OFF ]` so you never lose track of your automation states.
- **Focus Hijack:** Automatically reclaims the smartband's media controls whenever third-party media is paused on your phone.
- **⚠️ Experimental Hardware Modules:** Silently snap photos or record audio straight from your wrist (currently in testing phase, but fully functional).

---

### ⌚ Compatibility Note
> **Tested Device:** This application was explicitly developed and tested using a **Samsung Galaxy Fit 3**. However, **it will most likely work** on almost any smartband or smartwatch model capable of handling standard native media controls from Android.

---

### 🛠️ Installation & Sideloading
Because Band Trigger is an indie open-source tool that requests powerful permissions (like Notification Interception and Camera), **Google Play Protect may display a warning** during installation. 
* To proceed, simply tap **"More details"** and select **"Install anyway"**, or temporarily disable Play Protect in your Play Store settings.

---

### 👤 How to Use & Tutorials

For detailed setup guides, check our documentation:
* [Smart Home Setup Tutorial](docs/SMART_HOME.md)
* [PC Media Server Tutorial](docs/PC_SERVER.md) ( SOON )

1. **Download & Install:** Get the latest signed `.apk` from the Releases tab.
2. **Grant Permissions:** Open the **Settings** tab in the app. 
   - Enable **Hijack Band Focus** (this will redirect you to Android's Notification Access. Grant it so the app can intercept media events).
   - If you want to use the experimental modules, enable the Camera and Audio permissions here as well.
3. **Create Automations:** Go to the **Automations** tab and hit the `+` button.
   - *For Smart Home:* Select "HTTP Webhook" and paste your trigger URLs.
   - *For PC Media:* Use your local IP with the Python script (e.g., `http://192.168.x.x:5000/playpause`).
   - *For Hardware:* Select "Hidden Camera" or "Audio Recorder".
4. **Trigger it!** Pause any music playing on your phone. Band Trigger will hijack the watch screen. Use the Play/Pause or Skip buttons on your wrist to fire your routines!

---

### 💻 For Developers: Technical Overview

**Architecture**
- **UI & Navigation:** Built with a modular Fragment-based architecture (`SettingsFragment`, `AutomationsFragment`, `AboutFragment`) and smooth swipe transitions using `ViewPager2`.
- **Media Session Interception & Smart Toggle:** Uses `NotificationListenerService` and `MediaSessionManager` to detect paused media. It pushes an empty, high-priority media session to hijack the smartband display and dynamically updates the `MediaMetadata` to reflect ON/OFF states.
- **Python Integration:** Includes a lightweight `server/` script utilizing `pyautogui` to expand the Android ecosystem into desktop control.

---

### 💬 Project Scope & Limitations Note
> I have done my absolute best to build this project with the highest possible level of polish and reliability within the constraints of an independent solo development. **Feature suggestions are always welcome!** However, please keep in mind that due to architectural and Android system limitations, every new request must be carefully studied to determine if its implementation is feasible.

---

## ☕ Support the Project

If this app helped you, please help me too! 

[![Buy Me A Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-ffdd00?style=for-the-badge&logo=buy-me-a-coffee&logoColor=black)]((https://ko-fi.com/dafaolcreations))

---
---

## 🇧🇷 Português

### 💡 *"Smartbands foram feitas para contar passos e pular músicas. Eu decidi que elas podiam fazer mais."*

O Band Trigger é um aplicativo Android criado para dar um propósito totalmente novo ao seu dispositivo vestível. Ele intercepta os eventos de controle de mídia da sua smartband e os traduz em requisições HTTP personalizadas e comandos de hardware, transformando sua pulseira inteligente em um poderoso controle remoto.

---

### ✨ Funcionalidades
- **Webhooks para Casa Inteligente:** Controle luzes, tomadas ou rotinas do IFTTT.
- **Controle de Mídia do PC:** Dê play, pause ou pule músicas no seu desktop usando o servidor Python autônomo incluso no projeto.
- **Visor Inteligente (Smart Toggle):** Feedback em tempo real no pulso! O app atualiza o nome da música para mostrar `[ ON ]` (Ligado) ou `[ OFF ]` (Desligado), para você nunca perder o estado da sua automação.
- **Hijack de Foco:** Assume automaticamente os controles de mídia da smartband sempre que qualquer outra mídia é pausada no celular.
- **⚠️ Módulos de Hardware (Experimentais):** Tire fotos silenciosas ou grave áudio direto do pulso (atualmente em fase de testes, mas plenamente funcionais).

---

### ⌚ Nota de Compatibilidade
> **Dispositivo Testado:** Este aplicativo foi desenvolvido e testado utilizando estritamente um **Samsung Galaxy Fit 3**. No entanto, **provavelmente funcionará** em praticamente qualquer modelo de smartband ou smartwatch que possua suporte aos comandos nativos de controle de mídia do Android.

---

### 🛠️ Instalação e Aviso do Play Protect
Como o Band Trigger é uma ferramenta open-source independente que solicita permissões profundas (como Interceptação de Notificações e Câmera), **o Google Play Protect pode exibir um aviso de bloqueio** durante a instalação. 
* Para prosseguir, basta tocar em **"Mais detalhes"** e selecionar **"Install anyway / Instalar assim mesmo"**, ou desativar temporariamente a verificação nas configurações da Play Store.

---

### 👤 Como Usar e Tutoriais

Para guias de configuração detalhados, acesse a documentação específica:
* [Tutorial para Casa Inteligente (Lâmpadas, Tomadas)](docs/SMART_HOME.md)
* [Tutorial para Servidor de Mídia no PC](docs/PC_SERVER.md) ( EM BREVE )

1. **Baixe e Instale:** Baixe o `.apk` assinado mais recente na aba de Releases.
2. **Conceda as Permissões:** Abra a aba **Settings** no app.
   - Ative o **Hijack Band Focus** (isso abrirá as configurações do Android. Conceda o acesso às notificações para o app interceptar os comandos).
   - Se quiser usar os módulos experimentais, ative as permissões de Câmera e Áudio nesta mesma tela.
3. **Crie Automações:** Vá para a aba **Automations** e toque no botão `+`.
   - *Para Casa Inteligente:* Selecione "HTTP Webhook" e cole as URLs da sua lâmpada/dispositivo.
   - *Para Mídia do PC:* Use seu IP local apontando para o script Python (ex.: `http://192.168.x.x:5000/playpause`).
   - *Para Hardware:* Selecione "Câmera Oculta" ou "Gravador de Áudio".
4. **Acione!** Pause qualquer música tocando no seu celular. O Band Trigger assumirá a tela do relógio. Use os botões de Play/Pause ou Avançar/Voltar no seu pulso para disparar suas rotinas!

---

### 💻 Para Desenvolvedores: Visão Técnica

**Arquitetura**
- **UI e Navegação:** Construído com arquitetura modular baseada em Fragments (`SettingsFragment`, `AutomationsFragment`, `AboutFragment`) e transições de tela com `ViewPager2`.
- **Interceptação de Mídia e Smart Toggle:** Utiliza `NotificationListenerService` e `MediaSessionManager` para detectar mídias pausadas. Ele envia uma sessão de mídia vazia com prioridade máxima para assumir o controle do relógio e atualiza os metadados (ON/OFF) dinamicamente.
- **Integração com Python:** Inclui um script leve na pasta `server/` que utiliza `pyautogui` para expandir o ecossistema do Android até o desktop.

---

### 💬 Nota sobre Escopo e Limitações
> Fiz o meu possível para entregar a experiência mais polida e funcional possível dentro das limitações de um projeto solo. **Sugestões de novas funcionalidades são sempre muito bem-vindas!** Contudo, ressalto que há limitações técnicas inerentes à arquitetura e ao ecossistema, portanto a viabilidade de cada pedido precisa ser estudada caso a caso.

---

## ☕ Apoie o Projeto

Se este aplicativo te ajudou, me ajude também!

[![Buy Me A Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-ffdd00?style=for-the-badge&logo=buy-me-a-coffee&logoColor=black)]((https://ko-fi.com/dafaolcreations))
