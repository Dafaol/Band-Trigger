# 🚀 Band Trigger (v1.5.2)

🌎 *Choose your language / Escolha seu idioma:* 

[![English](https://img.shields.io/badge/English-blue?style=for-the-badge)](#-english)
[![Português](https://img.shields.io/badge/Portugu%C3%AAs-green?style=for-the-badge)](#-português)

---

## 🇺🇸 English

### 💡 *"Smartbands were designed to track steps and skip songs. I decided they could do more."*

Band Trigger is an Android application designed to expand the capabilities of your smartband. It intercepts media control events (play, pause, skip) from your wearable and translates them into custom commands for your smart home, PC, or device hardware.

---

### ✨ Features
- **Smart Home Webhooks:** Control smart lights, plugs, or IFTTT routines easily.
- **Wake on LAN (WoL):** Turn on your PC remotely directly from your wrist.
- **Folders & Custom Layout:** Organize your automations into folders. Drag and drop items to customize your layout exactly how you want it (saved automatically).
- **Smart Display:** Get real-time feedback. The app updates the track title on your watch to show `[ ON ]` or `[ OFF ]` so you always know your automation states.
- **Auto-Focus:** Automatically reclaims the smartband's media controls whenever a third-party media app is paused on your phone.
- **⚠️ Hardware Modules (Experimental):** Take photos silently or record audio right from your wrist (currently in testing, but fully functional).

---

### ⌚ Compatibility Note
> **Tested Device:** Developed and primarily tested on a **Samsung Galaxy Fit 3**. However, it should work on almost any smartband or smartwatch that supports standard Android media controls.

---

### 🛠️ Installation
Because Band Trigger is an independent open-source tool requesting advanced permissions (like Notification Access and Camera), **Google Play Protect may display a warning** during installation. 
* To proceed, simply tap **"More details"** and select **"Install anyway"**.

---

### 👤 How to Use

For detailed setup guides:
* [Smart Home Setup Tutorial](docs/SMART_HOME.md)

1. **Download:** Get the latest `.apk` from the Releases tab.
2. **Permissions:** Open the **Settings** tab in the app. 
   - Enable **Hijack Band Focus** (this requests Notification Access so the app can intercept watch commands).
   - If you want to use the hardware features, enable Camera and Audio permissions here as well.
3. **Create Automations:** Go to the **Automations** tab and hit the `+` button.
   - *For Organization:* Select "Create Folder" and drag items to reorder them.
   - *For Smart Home:* Select "HTTP Webhook" and paste your trigger URL.
   - *For PC Power:* Select "Wake on LAN (PC)" and input your computer's MAC Address.
   - *For Hardware:* Select "Hidden Camera" or "Audio Recorder".
4. **Trigger it!** Pause any media playing on your phone. Band Trigger will take over the watch screen. Use the media buttons on your wrist to run your automations.

---

### 💻 For Developers: Technical Overview

**Architecture**
- **UI & Navigation:** Fragment-based architecture (`Settings`, `Automations`, `About`) with `ViewPager2` transitions. Features a `RecyclerView` with `ItemTouchHelper` for drag-and-drop folder management.
- **Media Interception:** Uses `NotificationListenerService` and `MediaSessionManager` to detect paused media. It pushes an empty, high-priority media session to take over the smartband display, updating the `MediaMetadata` dynamically to reflect ON/OFF states.

---

### 💬 Scope & Limitations
> I have done my best to make this app reliable and efficient within the constraints of a solo independent project. **Feature suggestions are welcome!** Keep in mind that due to Android and smartwatch system limitations, the feasibility of every request must be carefully evaluated.

---

## ☕ Support the Project

If this app helped you, consider supporting the development! 

[![Ko-fi](https://img.shields.io/badge/Ko--fi-F16061?style=for-the-badge&logo=ko-fi&logoColor=white)](https://ko-fi.com/dafaolcreations)

---
---

## 🇧🇷 Português

### 💡 *"Smartbands foram feitas para contar passos e pular músicas. Eu decidi que elas podiam fazer mais."*

O Band Trigger é um aplicativo Android criado para expandir as capacidades da sua smartband. Ele intercepta os controles de mídia (play, pause, avançar) do seu relógio e os transforma em comandos customizados para sua casa inteligente, seu PC ou para o hardware do celular.

---

### ✨ Funcionalidades
- **Webhooks (Casa Inteligente):** Controle luzes, tomadas ou rotinas do IFTTT com facilidade.
- **Wake on LAN (WoL):** Ligue seu PC remotamente direto do pulso.
- **Pastas e Layout Customizável:** Organize suas automações em pastas. Arraste e solte os itens para personalizar a ordem do seu jeito (o layout é salvo automaticamente).
- **Visor Inteligente:** Feedback em tempo real. O app atualiza o nome da música no relógio para mostrar `[ ON ]` (Ligado) ou `[ OFF ]` (Desligado), indicando o estado atual da automação.
- **Foco Automático:** Assume os controles de mídia da smartband sempre que uma música ou vídeo for pausado no celular.
- **⚠️ Módulos Nativos (Experimentais):** Tire fotos silenciosas ou grave áudio através dos botões do pulso (em fase de testes, mas totalmente funcional).

---

### ⌚ Nota de Compatibilidade
> **Dispositivo Testado:** Desenvolvido e testado principalmente em uma **Samsung Galaxy Fit 3**. No entanto, deve funcionar na maioria das smartbands e smartwatches que suportam controles de mídia nativos do Android.

---

### 🛠️ Instalação
Como o app é uma ferramenta open-source independente e requer permissões avançadas (como acesso a Notificações e Câmera), o **Google Play Protect pode exibir um aviso** durante a instalação. 
* Para prosseguir, basta tocar em **"Mais detalhes"** e depois em **"Instalar assim mesmo"**.

---

### 👤 Como Usar

Para um guia mais detalhado:
* [Tutorial Casa Inteligente (Lâmpadas, Tomadas)](docs/SMART_HOME.md)

1. **Baixar:** Baixe o `.apk` mais recente na aba de Releases.
2. **Permissões:** Abra a aba **Settings** no app.
   - Ative o **Hijack Band Focus** (isso solicitará acesso às notificações para que o app consiga ler os comandos do relógio).
   - Se quiser usar os recursos de hardware, ative as permissões de Câmera e Áudio nesta mesma tela.
3. **Criar Automações:** Vá na aba **Automations** e toque no botão `+`.
   - *Para Organizar:* Escolha "Create Folder" e arraste os itens para reordená-los.
   - *Para Casa Inteligente:* Escolha "HTTP Webhook" e insira a URL do seu dispositivo.
   - *Para Ligar o PC:* Escolhe "Wake on LAN (PC)" e digite o MAC Address da sua placa de rede.
   - *Para Hardware:* Escolha "Câmera Oculta" ou "Gravador de Áudio".
4. **Acione!** Pause qualquer mídia que estiver tocando no celular. O app assumirá a tela do relógio. Use os botões no pulso para disparar suas automações.

---

### 💻 Para Desenvolvedores: Visão Técnica

**Arquitetura**
- **UI e Navegação:** Arquitetura baseada em Fragments (`Settings`, `Automations`, `About`) com transições usando `ViewPager2`. Sistema de drag-and-drop integrado com `RecyclerView` e `ItemTouchHelper` para o gerenciamento de pastas.
- **Interceptação de Mídia:** Utiliza `NotificationListenerService` e `MediaSessionManager` para detectar pausas. Ele envia uma sessão de mídia vazia de alta prioridade para assumir a tela do relógio, alterando os metadados (ON/OFF) de forma dinâmica.

---

### 💬 Limitações
> Me esforcei para entregar um aplicativo estável e eficiente dentro das limitações de um projeto solo independente. **Sugestões de novas funcionalidades são sempre bem-vindas!** Tenham em mente que o sistema Android e os relógios possuem restrições técnicas, portanto a viabilidade de cada pedido precisa ser avaliada cuidadosamente.

---

## ☕ Apoie o Projeto

Se o app foi útil para você, considere apoiar o desenvolvimento!

[![Ko-fi](https://img.shields.io/badge/Ko--fi-F16061?style=for-the-badge&logo=ko-fi&logoColor=white)](https://ko-fi.com/dafaolcreations)
