# ⌚💡 Band Light Connect
Um app Android open-source simples para controlar lâmpadas inteligentes usando os botões de mídia de qualquer smartband ou relógio.

## 🏍️ A Origem do Projeto
Eu trabalho de madrugada e utilizo uma moto para locomoção. Sempre chego às 05:15 da manhã (ainda de noite). Quase sempre meu celular vem guardado no fundo da mochila, seja por causa da chuva ou porque o bolso da calça é curto e não me passa segurança de que o aparelho não vai cair no meio do caminho.

Ao chegar em casa, tem todo aquele processo de tirar o capacete e as luvas em dias sem chuva, porque se estiver chovendo tem que tirar a capa de chuva também. Isso estava me deixando incomodado, porque não tenho mãos suficientes para pegar o celular e ligar a lâmpada do quarto para enxergar o caminho, e por algum motivo eu tenho vergonha de usar comando de voz. 

Certa vez, ao ir dormir depois de todo esse processo, uma onda de pensamentos tomou conta da minha mente: *"Caramba, seria tão bom se a minha smartband pudesse ligar a lâmpada do quarto com 1 toque. Que triste, a única comunicação que minha smartband tem com o celular é pelo controle de mídia... espera aí, e se eu tentar interceptar esses comandos que o relógio manda para o celular para fazer outra coisa?"* E foi aí, antes de dormir, que surgiu a ideia.

## 🛠️ Como funciona a "gambiarra" inteligente
A maioria das pulseiras genéricas consegue controlar a mídia do celular (pausar, pular música). O Band Light Connect se aproveita exatamente dessa função.

O app cria um "player de música fantasma" (usando a classe `MediaSession` do Android). O relógio acha que está controlando uma música do celular, mas na verdade o aplicativo intercepta esses cliques e os transforma em ações para a lâmpada:

* **Play / Pause:** Liga ou desliga a luz.
* **Avançar (Next):** Aumenta o brilho.
* **Voltar (Previous):** Diminui o brilho.

## 🎯 Pra quem é útil?
Eu criei isso para resolver o meu problema na moto, mas o projeto acaba servindo para qualquer pessoa que:

* Quer controlar a casa pelo pulso, mas não quer gastar uma fortuna em um smartwatch caro.
* Odeia ter que gritar comandos de voz para acender a luz no meio da noite.
* Costuma estar com o celular inacessível e precisa de um atalho rápido no pulso.
