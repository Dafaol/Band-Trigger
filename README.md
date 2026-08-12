# ⌚ Band Light Connect 💡

O **Band Light Connect** é um aplicativo Android desenvolvido para integrar smartbands (como o Galaxy Fit 3) com dispositivos de casa inteligente (lâmpadas Positivo / ecossistema Smart Life). 

Ele atua de forma invisível no celular, interceptando os comandos de mídia do relógio (Play/Pause) e transformando-os em gatilhos de automação (Webhooks) para acender ou apagar as luzes do ambiente.

## 🚀 Funcionalidades (Versão 0.9)

* **Serviço em Segundo Plano:** O aplicativo roda um `MediaSession` oculto, enganando o sistema Android para que o relógio acredite que há uma música tocando.
* **Controle pelo Pulso:** Permite ligar e desligar a luz utilizando o botão central (Play/Pause) da interface de mídia da smartband.
* **Configuração Universal:** Interface simples para o usuário colar seus próprios links de Webhook, salvando as informações nativamente na memória do aparelho via `SharedPreferences`.
* **Sem Custos:** Projetado para funcionar com a plataforma gratuita Sequematic, contornando limitações pagas de serviços como o IFTTT.

## 🛠️ Como configurar e usar

1. **Crie suas automações:**
   * Crie uma conta gratuita no [Sequematic](https://sequematic.com/).
   * Vincule sua conta da **Smart Life**.
   * Crie duas sequências usando o gatilho de **Custom Webhook**: uma para acender a lâmpada (Turn On) e outra para apagar (Turn Off). O Sequematic irá gerar dois links únicos.

2. **Configure o App:**
   * Instale o arquivo `.apk` no seu celular Android.
   * Abra o Band Light Connect.
   * Cole o link de Ligar no primeiro campo e o link de Desligar no segundo campo.
   * Clique em **Salvar Configurações**.

3. **Controle a Luz:**
   * Abra o controlador de mídia na sua smartband.
   * Aperte o botão de **Play/Pause** para alternar o estado da sua lâmpada inteligente.

## 💻 Tecnologias Utilizadas
* **Linguagem:** Kotlin
* **IDE:** Android Studio
* **Integrações:** Webhooks (HTTP GET requests), API Smart Life via Sequematic.
* **Componentes Android:** `MediaSession`, `Service`, `SharedPreferences`, `HttpURLConnection`.

---
*Desenvolvido por Daniel Fagundes de Oliveira*
