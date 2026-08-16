# 🛠️ Tutorial: Automações com Band Trigger & Sequematic

🌎 *Choose your language / Escolha seu idioma:* 
* [🇺🇸 English Version](#-english)
* [🇧🇷 Versão em Português](#-português)

---

## 🇺🇸 English

In this tutorial, you will learn how to connect your smartband to a smart bulb or plug using **Band Trigger** and **Sequematic** (a free bridge service for Tuya, Smart Life, eWeLink, etc.).

### 📋 Prerequisites:
1. **Band Trigger** app installed with permissions granted.
2. A free account on [Sequematic.com](https://sequematic.com/).
3. Your smart bulb already configured and working in its native app on your phone.

---

### Step 1: Linking your Smart Home account (Linked Services)

Before creating automations, allow Sequematic to access your devices.

1. In Sequematic, go to the top menu and click **Settings** > **Linked services**.
2. Find the service/app that controls your bulb (e.g., Tuya Smart, Smart Life, eWeLink) and log in to link it.

![Sequematic Linked Services](tutorial-images/smart-home/tutorial_sequematic_linked-services.png)

> 💡 **Important Note:** Many generic smart bulb apps run on the **Tuya** or **Smart Life** system under the hood. If your specific app isn't listed, try setting up your bulb directly in the Smart Life app to ensure compatibility!

---

### Step 2: Creating the Webhook in Sequematic

A "Webhook" is a custom URL. When Band Trigger accesses this URL, it triggers a command in Sequematic.

1. Go to the Sequematic dashboard and click **New Sequence**.
2. Name the sequence to identify the action (e.g., "Turn On Bedroom Light").
3. Under the **Trigger** section, click **Add Step**.
4. Choose **Webhook**, then **Custom webhook**, and click **Save**.
5. Sequematic will generate a unique URL for you (e.g., `https://sequematic.com/trigger/...`). **Copy this URL!**

![Sequematic Webhook Screen](tutorial-images/smart-home/tutorial_sequematic_link-web-hook.png)

---

### Step 3: Adding the Action (Turn on the light)

Now, configure Sequematic to act when that link is triggered.

1. Under the **Steps** section of the same page, click **Add step**.
2. Click on **Smart device** > **Smart device** and choose the service you linked in Step 1 (e.g., Tuya Smart).
3. Select your device directly from the list and choose the action (e.g., *Turn On*). 
   *(Note: Sequematic also lists what you create in the app; for instance, with the Smart Life app, the scenes you create appear there, and you can control those scenes as well).*
4. Click **Save** at the bottom of the page to store your sequence.

![Sequematic Configured Automation](tutorial-images/smart-home/tutorial_sequematic_configured-automation.png)

*(Tip: Since we need one action to turn on and another to turn off, repeat Steps 2 and 3 creating a **new sequence** with the name "Turn Off Bedroom Light" and the "Turn Off" action. This will generate a second, unique link for turning it off).*

---

### Step 4: Configuring Band Trigger

Insert the magic links into the app.

1. Open **Band Trigger** and navigate to the **Automations** tab.
2. Tap the **`+`** floating button.
3. Leave **Action Type** as `HTTP Webhook`.
4. Name your automation (e.g., `Bedroom Light`).
5. In the **Turn On URL** field, paste the long URL you copied from Sequematic for turning the light ON.
6. In the **Turn Off URL** field, paste your second sequence link for turning the light OFF.
7. Tap **Save**.

| Automations Menu | New Automation |
| :---: | :---: |
| ![Automations Menu](tutorial-images/smart-home/tutorial_band_trigger_automation-menu.png) | ![New Automation](tutorial-images/smart-home/tutorial_band_trigger_new-automation.png) |

---

### Step 5: Testing the Magic! 🪄

1. Play a song on your phone (Spotify, YouTube, etc.) and then **pause it**.
2. **Band Trigger** will immediately hijack your smartband's media screen.
3. On your watch, tap the **Play / Pause** button.
4. The app will silently trigger the link alternating between "ON" and "OFF", Sequematic will catch it, and your light will turn on or off!

<p align="center">
  <img src="tutorial-images/smart-home/tutorial_band_trigger_galaxy-fit-3.png" width="400" />
</p>

---
---

## 🇧🇷 Português

Neste tutorial, você vai aprender como conectar a sua smartband a uma lâmpada ou tomada inteligente usando o **Band Trigger** e o site **Sequematic** (que serve como uma ponte gratuita entre o seu celular e dispositivos da Tuya, Smart Life, eWeLink, etc.).

### 📋 O que você vai precisar:
1. O aplicativo **Band Trigger** instalado e com as permissões concedidas.
2. Uma conta gratuita no site [Sequematic.com](https://sequematic.com/).
3. A lâmpada já configurada e funcionando no aplicativo original da fabricante no seu celular.

---

### Passo 1: Vinculando sua conta de Casa Inteligente (Linked Services)

Antes de criar a automação, é preciso dar permissão para o Sequematic acessar seus dispositivos.

1. No Sequematic, vá até o menu superior e clique em **Settings** > **Linked services**.
2. Encontre o aplicativo que controla sua lâmpada (Ex: Tuya Smart, Smart Life, eWeLink) e faça o login para vinculá-lo.

![Tela de Linked Services](tutorial-images/smart-home/tutorial_sequematic_linked-services.png)

> 💡 **Nota Importante:** Muitos aplicativos de lâmpadas genéricas usam o sistema da **Tuya** ou **Smart Life** por baixo dos panos. Se o app específico da sua lâmpada não estiver na lista do Sequematic, configure sua lâmpada diretamente pelo app Smart Life no celular para garantir a compatibilidade!

---

### Passo 2: Criando o Webhook no Sequematic

Um "Webhook" é um link (URL) que, quando acessado pelo Band Trigger, aciona um comando no Sequematic.

1. Acesse a tela inicial do Sequematic e clique em **New Sequence** (Nova Sequência).
2. Nomeie a sequência para identificar a ação (Ex: "Ligar Lâmpada Quarto").
3. Na seção de **Trigger** (Gatilho), clique em **Add Step**.
4. Escolha a opção **Webhook**, depois **Custom webhook** e clique em **Save**.
5. O Sequematic vai gerar uma URL única para você (algo como `https://sequematic.com/trigger/...`). **Copie essa URL!**

![Tela do Webhook no Sequematic](tutorial-images/smart-home/tutorial_sequematic_link-web-hook.png)

---

### Passo 3: Adicionando a Ação (Ligar a luz)

Agora, configure o Sequematic para agir quando esse link for acionado.

1. Na seção **Steps** (Passos/Ações) da mesma tela, clique em **Add step**.
2. Clique em **Smart device** > **Smart device** e escolha o serviço vinculado no Passo 1 (Ex: Tuya Smart / Smart Life).
3. Selecione o dispositivo que você quer controlar diretamente na lista e a ação (Ex: *Turn On* para ligar). 
   *(Nota: O Sequematic também lista o que você criar dentro do app. No caso do app Smart Life, as cenas que você criar aparecerão ali e você poderá controlá-las também).*
4. Clique em **Save** (Salvar) no final da página para guardar sua sequência.

![Automação Configurada no Sequematic](tutorial-images/smart-home/tutorial_sequematic_configured-automation.png)

*(Dica: Como precisamos de uma ação para ligar e outra para desligar, repita os Passos 2 e 3 criando uma **nova sequência** no Sequematic com o nome "Desligar Lâmpada Quarto" e selecionando a ação "Turn Off". Isso gerará um segundo link exclusivo para desligar).*

---

### Passo 4: Configurando o Band Trigger

Insira os links mágicos dentro do aplicativo.

1. Abra o **Band Trigger** e vá até a aba **Automations** (Automações).
2. Toque no botão flutuante **`+`**.
3. No campo **Action Type**, deixe marcado como `HTTP Webhook`.
4. Dê um nome para sua automação (Ex: `Luz do Quarto`).
5. No campo **Turn On URL**, cole aquele link longo que você copiou do Sequematic para LIGAR a luz.
6. No campo **Turn Off URL**, cole o segundo link que você criou para DESLIGAR a luz.
7. Toque em **Save**.

| Menu de Automações | Nova Automação |
| :---: | :---: |
| ![Menu de Automações](tutorial-images/smart-home/tutorial_band_trigger_automation-menu.png) | ![Nova Automação](tutorial-images/smart-home/tutorial_band_trigger_new-automation.png) |

---

### Passo 5: Testando a Mágica! 🪄

1. Coloque uma música para tocar no seu celular (Spotify, YouTube, etc.) e depois **pause a música**.
2. O **Band Trigger** vai assumir a tela da sua smartband imediatamente.
3. No seu relógio, toque no botão de **Play / Pause**.
4. O app vai acessar o link silenciosamente alternando entre o "ON" e o "OFF", o Sequematic vai receber o aviso e a sua luz vai acender ou apagar!

<p align="center">
  <img src="tutorial-images/smart-home/tutorial_band_trigger_galaxy-fit-3.png" width="400" />
</p>
