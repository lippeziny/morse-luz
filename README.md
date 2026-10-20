# Morse Luz 📡

App Android nativo para enviar mensagens em código Morse usando o flash e receber mensagens analisando a luz pela câmera em tempo real.

## O que já está incluído

- **Enviar:** digitação de até 240 caracteres, prévia em Morse, três velocidades e transmissão pelo flash traseiro.
- **Receber:** prévia ao vivo da câmera, detecção de pulsos claros/escuros, decodificação adaptativa e botão para copiar o texto.
- **Interface:** duas abas, visual clean, offline e sem cadastro.
- **Automação:** GitHub Actions compila um APK de debug em cada push para `main`/`master`, pull request ou execução manual.
- **Testes:** testes unitários para normalização de acentos, prévia Morse e temporização dos pulsos.

## Como funciona

O remetente usa a convenção internacional de Morse: ponto = 1 unidade de luz, traço = 3 unidades; separações entre sinais, letras e palavras seguem intervalos maiores. O leitor mede mudanças no brilho da região central da imagem e estima o ritmo observado.

**Para testar entre dois celulares:** instale o app nos dois. No aparelho emissor, abra **Enviar**, digite o texto e toque em **Piscar mensagem**. No receptor, abra **Receber**, toque em **Iniciar leitura** e mantenha o flash do outro celular dentro do retângulo central. O melhor resultado ocorre com os aparelhos relativamente estáveis, sem luz forte diretamente sobre a câmera.

A leitura óptica depende da câmera, exposição automática, distância e iluminação do ambiente. O leitor pode precisar de testes adicionais em alguns modelos; não use este protótipo como canal de comunicação de emergência.

## Compilar no Android Studio

1. Instale uma versão atual do Android Studio com suporte ao Android SDK 36 e JDK 17.
2. Abra a pasta `morse-luz` como projeto existente.
3. Aguarde a sincronização do Gradle e aceite a instalação dos componentes do SDK solicitados.
4. Execute `app` em um dispositivo Android com câmera e flash.

Os scripts `gradlew` e `gradlew.bat` deste pacote baixam o Gradle 8.13 na primeira execução quando não há uma instalação local. Em máquinas de desenvolvimento com Gradle já instalado, eles usam essa instalação.

## Compilar com GitHub Actions

1. Crie um repositório no GitHub e envie todos os arquivos desta pasta.
2. Abra a aba **Actions** e habilite workflows, se o GitHub solicitar.
3. O workflow `Build Android APK` executa automaticamente em cada push para `main`/`master` e em pull requests. Também pode ser iniciado por **Run workflow**.
4. Ao terminar, abra a execução mais recente, vá até **Artifacts** e baixe `morse-luz-debug-apk`.
5. Extraia o ZIP do artefato. O APK será `app-debug.apk`.

O APK de debug é apropriado para testes e instalação manual; uma publicação na Play Store precisa de uma build release assinada.

## Stack

- Kotlin
- Jetpack Compose + Material 3
- CameraX
- Gradle 8.13 / Android Gradle Plugin 8.13.2
- JDK 17

## Permissões

O app solicita acesso à câmera para controlar o flash e analisar o brilho. O processamento acontece no dispositivo; não há envio de imagens nem de mensagens para servidores.
