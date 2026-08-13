# 🚀 Band Trigger

🌎 *Choose your language / Escolha seu idioma:* 

[![English](https://img.shields.io/badge/English-blue?style=for-the-badge)](#-english)
[![Português](https://img.shields.io/badge/Portugu%C3%AAs-green?style=for-the-badge)](#-português)

---

## 🇺🇸 English
> **Smartbands were designed to track steps and skip songs. I decided they could do more.**

Band Trigger is an Android application built to give a completely new purpose to your wearable device. It intercepts media control events from your smartband and translates them into custom HTTP requests, effectively turning your simple fitness band into a powerful smart home remote.

### 👤 For Users: The Story & Usage

#### The Story: Unlocking the Wearable
Most fitness bands are locked into their own ecosystems, with buttons restricted to basic functions. *Band Trigger* was born out of the idea to bypass these limitations without needing complex hacks or custom firmware. By cleverly hijacking the native "Play/Pause" media controls, this app empowers you to control your lights, PC, or any custom routine directly from your wrist.

#### Basic Usage
1. Add your custom Webhook URLs (Turn ON / Turn OFF) in the **Automations** tab.
2. Enable **Hijack Band Focus** in the Settings.
3. Pause any media on your phone to let Band Trigger take over the smartband screen.
4. Use the Play/Pause buttons on your wrist to trigger your automations.

📚 **Note:** For detailed step-by-step guides, troubleshooting, and setup tutorials, please check the [Wiki / Docs Folder](link-to-wiki-or-docs).

### 💻 For Developers: Technical Overview

#### Architecture
- **UI & Navigation:** Built with a modular Fragment-based architecture (`SettingsFragment`, `AutomationsFragment`, `AboutFragment`) and smooth swipe transitions using `ViewPager2`.
- **Media Session Interception:** Uses `NotificationListenerService` and `MediaSessionManager` to detect when media is paused globally, pushing an empty, high-priority media session to hijack the smartband display.
- **Storage:** Automations are stored locally using `SharedPreferences` serialized via JSON.

---

## ☕ Support the Project

If this app helped you, please help me too! 

[![Buy Me A Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-ffdd00?style=for-the-badge&logo=buy-me-a-coffee&logoColor=black)](https://buymeacoffee.com/dafaol)

---
---

## 🇧🇷 Português

> **Smartbands foram feitas para contar passos e pular músicas. Eu decidi que elas podiam fazer mais.**

O Band Trigger é um aplicativo Android criado para dar um propósito totalmente novo ao seu dispositivo vestível. Ele intercepta os eventos de controle de mídia da sua smartband e os traduz em requisições HTTP personalizadas, transformando sua pulseira inteligente em um poderoso controle remoto para sua casa.

### 👤 Para Usuários: A História e Como Usar

#### A História: Desbloqueando o Potencial
A maioria das smartbands é limitada aos seus próprios ecossistemas. O *Band Trigger* nasceu da ideia de contornar essas limitações sem precisar de modificações complexas no sistema da pulseira. Utilizando a criatividade para "sequestrar" os controles nativos de mídia (Play/Pause), o aplicativo permite que você controle luzes, seu PC ou qualquer rotina da sua casa inteligente direto do pulso.

#### Uso Básico
1. Adicione os seus URLs de Webhook (Ligar / Desligar) na aba de **Automações**.
2. Ative a opção **Hijack Band Focus** na tela de Settings (Configurações).
3. Pause qualquer mídia no seu celular para que o Band Trigger assuma a tela da smartband.
4. Use os botões de Play/Pause no seu pulso para acionar suas automações.

📚 **Nota:** Para tutoriais passo a passo, resolução de problemas e guias de configuração, acesse a [Wiki / Pasta Docs](link-to-wiki-or-docs).

### 💻 Para Desenvolvedores: Visão Técnica

#### Arquitetura
- **UI e Navegação:** Construído com arquitetura modular baseada em Fragments (`SettingsFragment`, `AutomationsFragment`, `AboutFragment`) e transições de tela com `ViewPager2`.
- **Interceptação de Mídia:** Utiliza `NotificationListenerService` e `MediaSessionManager` para detectar quando a mídia é pausada globalmente, enviando uma sessão de mídia vazia com prioridade máxima para assumir o foco da tela da smartband.
- **Armazenamento:** Automações salvas localmente via `SharedPreferences` em formato JSON.

---

## ☕ Apoie o Projeto

Se este aplicativo te ajudou, me ajude também!

[![Buy Me A Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-ffdd00?style=for-the-badge&logo=buy-me-a-coffee&logoColor=black)](https://buymeacoffee.com/dafaol)
