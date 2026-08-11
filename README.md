## 🛠️ Como configurar e usar

1. **Gere as suas URLs de Webhook:**
   * O aplicativo funciona disparando links (HTTP GET Requests). Você pode gerar esses links usando o serviço de automação da sua preferência (Sequematic, IFTTT, Home Assistant, etc.).
   * **Dica para usuários Tuya/Smart Life (Marcas como Positivo, Elgin, Geonav, NovaDigital):** Recomendamos usar a plataforma gratuita [Sequematic](https://sequematic.com/). Crie uma conta, vincule o seu app de casa inteligente e crie duas sequências usando o gatilho **Custom Webhook** (uma ação para Turn On e outra para Turn Off). O Sequematic irá gerar os dois links únicos que você precisa.

2. **Configure o App:**
   * Instale o arquivo `.apk` no seu celular Android.
   * Abra o Band Light Connect.
   * Cole o link de Ligar no primeiro campo e o link de Desligar no segundo campo.
   * Clique em **Salvar Configurações**.

3. **Controle a Luz:**
   * Abra o controlador de mídia na sua smartband.
   * Aperte o botão de **Play/Pause** para acionar os Webhooks e alternar o estado do seu dispositivo inteligente.
