# Fit-HomeConnect
Um app para usar smartband para controlar lâmpadas

# ⌚💡 Fit HomeConnect

Bem-vindo ao **Fit HomeConnect**! Um aplicativo Android open-source criado para resolver um problema comum: a falta de controle de automação residencial em smartbands e relógios mais simples.

## 🎯 O Problema
Dispositivos como o Galaxy Fit 3 (e outras pulseiras inteligentes) possuem controles nativos excelentes para **mídia**, mas não oferecem suporte direto para aplicativos de casa inteligente (como o Google Home ou SmartThings).

## 🚀 A Solução
O **Fit HomeConnect** atua como uma ponte invisível. Ele simula uma "Sessão de Mídia" (MediaSession) no seu smartphone Android. Quando o seu relógio se conecta ao celular, ele reconhece o app como um player de música. 

Ao invés de pausar uma música, os botões do seu relógio são interceptados pelo aplicativo e transformados em comandos para as suas lâmpadas inteligentes:

*   **Pausar / Dar Play:** Liga e desliga a lâmpada inteligente.
*   **Avançar (Next):** Aumenta o brilho da lâmpada.
*   **Voltar (Previous):** Diminui o brilho da lâmpada.

## 🛠️ Tecnologias Utilizadas
*   **Android (Kotlin):** Linguagem principal do aplicativo.
*   **MediaSessionCompat:** Para gerar a notificação de mídia falsa e capturar os eventos do relógio.
*   **Integração Webhook / API HTTP:** Para enviar os comandos de execução para a lâmpada inteligente.

## 💡 Por que este projeto existe?
Este projeto nasceu da ideia de que não é preciso comprar um smartwatch caro e complexo apenas para ter o conforto de apagar as luzes do quarto pelo pulso. Com um pouco de criatividade e interceptação de eventos de mídia, qualquer smartband pode virar um controle remoto para sua casa.

## 🤝 Como Contribuir
(Em breve - O projeto está na fase inicial de estruturação da lógica).

