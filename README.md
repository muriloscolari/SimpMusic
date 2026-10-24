<div align="center">
  <img src="https://raw.githubusercontent.com/maxrave-dev/SimpMusic/dev/fastlane/metadata/android/en-US/images/featureGraphic.png" alt="SimpMusic Banner" width="800">
  <h1>SimpMusic (Enhanced Edition)</h1>
  <p><strong>Cliente de YouTube Music moderno, sem anúncios e com foco em fluidez extrema para Android e Desktop.</strong></p>
  <p>Fork aprimorado com suporte completo a 120 FPS, Android Auto reestruturado, widget nativo de alto desempenho, novos controles de reprodução e total liberdade de compilação.</p>

  <p>
    <a href="https://github.com/muriloscolari/SimpMusic/releases/latest">
      <img src="https://img.shields.io/github/v/release/muriloscolari/SimpMusic?style=for-the-badge&logo=github&color=blue" alt="Latest Release">
    </a>
    <a href="https://github.com/muriloscolari/SimpMusic/actions/workflows/custom-build-release.yml">
      <img src="https://img.shields.io/github/actions/workflow/status/muriloscolari/SimpMusic/custom-build-release.yml?branch=custom&style=for-the-badge&logo=githubactions&logoColor=white&label=Build%20Status" alt="Build Status">
    </a>
    <a href="LICENSE">
      <img src="https://img.shields.io/badge/License-GPL%20v3.0-green.svg?style=for-the-badge" alt="License: GPL v3">
    </a>
  </p>
</div>

---

## 🌟 Sobre este Fork

Este repositório é uma **versão aprimorada e mantida ativamente** por [@muriloscolari](https://github.com/muriloscolari), desenvolvida sobre a base do projeto [SimpMusic de maxrave-dev](https://github.com/maxrave-dev/SimpMusic).

O foco deste fork é elevar o padrão de usabilidade, desempenho e estabilidade do aplicativo para o uso diário, eliminando gargalos de performance, adicionando recursos práticos que faltavam no projeto original e removendo todas as distrações ou travas proprietárias.

---

## 🚀 Todas as Melhorias e Recursos Exclusivos deste Fork

Abaixo estão todas as otimizações e novos recursos desenvolvidos e incorporados nesta versão:

### 🏎️ 1. Desempenho Extremo e Fluidez (120 FPS cravados)
- **Suporte Nativo a Altas Taxas de Atualização (120 Hz / 120 FPS)**: Remoção das limitações de taxa de quadros e migração de renderização de vídeo para `TextureView`, desativando o rate-voting restritivo do ExoPlayer.
- **Otimização Drástica do Player Apple Music**: Eliminação de recomposições contínuas a cada 50ms e remoção de filtros de desfoque pesados redundantes que causavam engasgos e perda de frames.
- **Integração com ADPF (Android Dynamic Performance Framework)**: Implementação de sessões de *performance hint* para informar a CPU/GPU sobre a carga de renderização em tempo real, mitigando quedas de taxa de quadros (*stutters*).
- **Pausa de UI em Segundo Plano**: Quando a tela de reprodução está ativa, transições infinitas ociosas e recomposições das telas em segundo plano são congeladas para liberar ciclos de CPU.
- **Fluidez em Grandes Playlists**: Otimização profunda de paginação no banco de dados SQLite, transações em lote (*batch transactions*) e cálculo correto de offset de linhas para eliminar travamentos ao rolar bibliotecas com centenas de músicas.

### ☁️ 2. SimpSync Cloud: Sincronização em Tempo Real com Criptografia E2EE (Zero-Knowledge)
- **Criptografia Ponta a Ponta (Zero-Knowledge E2EE)**: Toda a biblioteca (músicas curtidas, playlists locais com fotos e ordem das faixas, artistas seguidos, álbuns favoritados, histórico de reprodução e configurações) é criptografada no cliente com **AES-256-GCM** antes de ser enviada ao servidor.
- **Chaveiro com Senha Mestra + Recovery Key**: Chave de dados (`DEK`) protegida por derivação **PBKDF2-HMAC-SHA256** (120.000 iterações) e com suporte a Chave de Recuperação (`XXXX-XXXX-XXXX-...`) para recuperação de conta caso a senha seja esquecida.
- **Auto-Push em Tempo Real (~1.5s)**: Ações principais da biblioteca (curtir uma música, alterar uma playlist, seguir um artista) disparam sincronização automática e silenciosa em ~1.5 segundo, sem precisar esperar nem sincronizar manualmente.
- **Notificações Instantâneas via WebSocket (~300ms)**: Ao curtir ou alterar uma playlist em um dispositivo (ex: PC), os outros aparelhos conectados (ex: celular Android) recebem o aviso via WebSocket em ~300ms e atualizam a interface automaticamente e em silêncio.
- **Gerenciamento de Dispositivos Conectados**: Interface completa em *Configurações -> Dispositivos* permitindo visualizar os aparelhos ativos na conta e revogar o acesso de sessões remotas.
- **Backend Próprio em Go Otimizado e Blindado**: Servidor auto-hospedável com consumo ínfimo de RAM (~10 MB), semáforos de concorrência Argon2id anti-OOM, rate limiter por IP, sincronização delta por hash e bloqueio de novos registros (`ALLOW_REGISTRATION=false`).

### 🚗 3. Reformulação do Android Auto
- **Navegação Visual com Capas de Álbuns**: Categorias, prateleiras e listas com arte em alta resolução na tela do carro.
- **Prateleira de Playlists na Categoria Início**: A categoria *Início* agora começa com uma prateleira de playlists; ao abri-la, as capas e a biblioteca completa aparecem em grade.
- **Categorias de Biblioteca**: Acesso rápido a *Downloaded*, *Favorites*, *Playlists* e *Songs*, na mesma ordem da interface nova.
- **Desduplicação de Curtidas**: Filtro automático para evitar que a playlist de curtidas do YouTube apareça repetida (*"Músicas que gostei"* e *"YouTube Liked Music"*).
- **Botão de Fila na Tela do Carro**: Botão dedicado de fila adicionado ao player em reprodução (*Now Playing*) para inspecionar e gerenciar as próximas faixas direto na multimídia do veículo.
- **Correção de Seleção de Faixas**: Solução definitiva para a falha em que clicar em uma música na tela do carro não tocava a faixa correta ou revertia para a música anterior.

> A interface nova com Car App Library (abas e seções) está preservada no branch `feat/car-cal-ui` (tag `car-cal-ui-v1`). No branch `custom`, o Android Auto usa a interface legada de `MediaBrowserService`, otimizada para ficar o mais próxima possível dela.

### 📱 3. Novo Widget Compacto Nativo (RemoteViews)
- **Arquitetura Leve em RemoteViews**: Substituição de frameworks pesados por RemoteViews nativo do Android, garantindo inicialização instantânea e menor consumo de RAM e bateria.
- **Arte do Álbum Ampliada em Alta Definição**: Aumento considerável no tamanho da capa da música com decodificação em alta qualidade.
- **Paleta Adaptativa com Cor Dominante**: O widget analisa a capa da música e ajusta a cor e o gradiente de fundo dinamicamente.
- **Layout de 2 Linhas**: Título da música e artista organizados em linhas separadas para evitar cortes de texto.
- **Roteamento Inteligente de Toques**: Tocar na capa ou no título abre diretamente o player em reprodução; botões de play/pause e skip operam instantaneamente.

### 🎧 4. Player, Rádio e Letras Inteligentes
- **Auto-Resume ao Pular Faixas**: Se a reprodução estiver pausada e você pular para a próxima música ou voltar para a anterior, ela começa a tocar automaticamente sem exigir um clique manual no botão de Play.
- **Transição Contínua para Rádio (*Seamless Radio*)**: Iniciar um rádio a partir da música atual faz uma transição perfeita sem recarregar nem interromper a reprodução da faixa que está tocando.
- **Priorização de Letras Palavra por Palavra (`RICH_SYNCED`)**: O motor de letras sincronizadas prioriza automaticamente fontes ricas palavra por palavra no SimpMusic Lyrics e LRCLIB.
- **Gesto de Voltar Preditivo (*Predictive Back*)**: Suporte nativo ao gesto de voltar do Android 14+ com animações de transição suaves.

### 📥 5. Downloads Concorrentes Configuráveis
- **Downloads Paralelos Ajustáveis**: Nova opção na tela de configurações para definir o número de downloads simultâneos (`maxParallelDownloads`), acelerando expressivamente o download de playlists e álbuns inteiros para o modo offline.

### 🖥️ 6. Player Desktop (Linux & Windows)
- **Ajuste de Volume com o Scroll do Mouse**: Altere o volume facilmente apenas rolando a roda do mouse sobre o ícone ou controle de volume.
- **Ajuste de Volume em Tempo Real (*Live Slider Drag*)**: A alteração sonora ocorre instantaneamente enquanto você arrasta a barra, permitindo encontrar o volume ideal de ouvido sem ter que soltar o clique.

### 🧹 7. Experiência Limpa e Sem Bloatware
- **Sem Auto-Promoção ou Popups**: Remoção de todos os banners de anúncios de repositório, popups de avaliação insistentes e convites forçados de redes sociais do desenvolvedor original.
- **Remoção da Trava de Assinatura Proprietária**: O código original continha uma verificação artificial impedindo o app de rodar se compilado com chaves de assinatura diferentes. Essa trava foi eliminada, garantindo total liberdade para compilar releases customizadas.

### ⚙️ 8. Pipeline de CI/CD Totalmente Automatizado
- **Releases Automáticas no GitHub Actions**: A cada commit no branch `custom`, o GitHub Actions compila e publica automaticamente os pacotes para todas as plataformas:
  - APK Android ARM64 (`arm64-v8a`)
  - APK Android Universal
  - AppImage portátil para Linux (`x86_64`)
  - Instalador offline para Windows (`x64`)
- **Changelog e Notas Detalhadas**: As notas de cada release trazem automaticamente o título e a descrição completa do commit que a gerou.

---

## 📥 Downloads

Baixe os binários da versão mais recente na nossa página de **[Releases](https://github.com/muriloscolari/SimpMusic/releases)**:

| Plataforma | Pacote | Recomendação |
| :--- | :--- | :--- |
| **Android (Smartphones modernos)** | `SimpMusic-v*-arm64-v8a.apk` | **Recomendado**: mais leve e otimizado para CPUs 64 bits. |
| **Android (Universal)** | `SimpMusic-v*-universal.apk` | Compatível com todos os dispositivos e emuladores Android. |
| **Linux** | `SimpMusic-v*-Linux-x86_64.AppImage` | Executável único compatível com qualquer distribuição Linux. |
| **Windows** | `SimpMusic-v*-Windows-x64-installer.zip` | Instalador offline com script automatizado para Windows 10/11. |

> [!WARNING]
> **Não é possível instalar por cima do SimpMusic oficial.** Esta é uma build assinada com **chave de debug** e mantém o mesmo `applicationId` (`com.maxrave.simpmusic`) do app original — o Android vai recusar a instalação (`App not installed` / `App não instalada`) por incompatibilidade de assinatura. **Desinstale a versão oficial antes de instalar esta** (o mesmo vale ao voltar para o oficial depois). Se quiser preservar seus dados, faça um backup antes.

---

## ✨ Recursos Herdados da Base

- Reprodução livre de anúncios e em segundo plano com áudio de alta fidelidade (Opus / AAC até 256 kbps).
- Equalizador de 10 bandas integrado com suporte a perfis de fone AutoEq, Reverb e Delay.
- Modo Offline e Cache inteligente para reprodução sem internet.
- Crossfade estilo DJ com transições suaves.
- Integração nativa com SponsorBlock e Return YouTube Dislike.
- Scrobbling no Last.fm.
- Suporte a múltiplos perfis e contas do YouTube.

---

## 🛠️ Compilação Local

### Pré-requisitos
- **JDK 21** ou superior
- **Android SDK** configurado na variável de ambiente `ANDROID_HOME`
- **Conveyor** (necessário para empacotar a distribuição Desktop)

### Comandos de Compilação

```bash
# Compilar APK de Release (Android)
./gradlew :androidApp:assembleRelease --no-configuration-cache

# Gerar e empacotar AppImage (Linux)
./gradlew :desktopApp:writeConveyorConfig --no-configuration-cache
conveyor --agree-to-license=1 -Kapp.machines=linux.amd64.glibc make --overwrite=HARD_REPLACE linux-app
./gradlew :desktopApp:packageConveyorAppImage --no-configuration-cache

# Executar a versão Desktop em modo desenvolvimento
./gradlew :desktopApp:run
```

---

## ⚖️ Disclaimer Legal e Termos de Uso

- **Fork não oficial:** este projeto **não é afiliado, endossado nem suportado** pelo [SimpMusic original](https://github.com/maxrave-dev/SimpMusic) nem por seus desenvolvedores.
- **Não relate problemas deste fork no projeto original.** Use as [Issues deste repositório](https://github.com/muriloscolari/SimpMusic/issues) para reportar bugs e enviar sugestões.
- **Fornecido "COMO ESTÁ" (AS IS):** sem garantia de qualquer tipo, expressa ou implícita. Os autores não se responsabilizam por qualquer dano, perda de dados ou prejuízo decorrente do uso deste software.
- **Sem incentivo à pirataria:** este projeto não apoia nem incentiva a pirataria. Os usuários são os **únicos responsáveis** por garantir que o uso esteja em conformidade com as leis de direitos autorais do seu país e com os Termos de Serviço das plataformas acessadas.
- **Código aberto (GPLv3):** o código-fonte completo das modificações está disponível neste repositório.

---

## 📄 Licença e Atribuição

- Este software é livre e de código aberto, licenciado sob a **[GNU General Public License v3.0 (GPLv3)](LICENSE)**.
- Desenvolvido a partir do projeto original [SimpMusic por maxrave-dev](https://github.com/maxrave-dev/SimpMusic).
- Agradecimentos especiais às comunidades dos projetos [InnerTune](https://github.com/z-huang/InnerTune), [SmartTube](https://github.com/yuliskov/SmartTube), [LRCLIB](https://lrclib.net/) e [SponsorBlock](https://sponsor.ajay.app/).
