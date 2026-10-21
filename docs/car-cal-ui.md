# UI nova do Android Auto (Car App Library)

Este branch preserva a interface nova do Android Auto, construída com a
Car App Library (`androidx.car.app`). Ela não é usada pelo branch `custom`,
que mantém somente a interface legada de `MediaBrowserService`.

## Branches e tag

- Parent: `feat/car-cal-ui`
- Submódulo `core`: `feat/car-cal-ui`
- Tag do conjunto: `car-cal-ui-v1` (parent e submódulo)

O parent aponta para o commit do submódulo que contém a UI CAL. Para restaurar
o conjunto completo:

```bash
git checkout feat/car-cal-ui
git submodule update --init --recursive
```

Se o submódulo ficar em HEAD destacado e você quiser trabalhar nele:

```bash
git -C core checkout feat/car-cal-ui
```

## O que está neste branch

- `core/media/media3/src/main/java/com/maxrave/media3/carapp/**`
  - `SimpMusicCarAppService`, sessão, telas de biblioteca, lista, fila,
    busca e now playing.
- Entradas no `AndroidManifest.xml`:
  - permissão `androidx.car.app.MEDIA_TEMPLATES`
  - feature `android.software.car.templates_host.media`
  - serviço `SimpMusicCarAppService` com `CarAppService` e
    `SHOW_MEDIA_PLAYBACK`
  - metadados `minCarApiLevel` e `TintableAttributionIcon`
- `automotive_app_desc.xml` com `<uses name="template" />`
- Dependências `androidx.car.app:app` e `app-projected`
- Regras de ProGuard/consumer rules da Car App Library

## Limite importante em carro real

Em um carro real, um app CAL só roda se tiver sido instalado de uma fonte
confiável (Google Play ou ONE store). "Fontes desconhecidas" não libera CAL.
Um APK sideload continua funcionando no Android Auto, mas pela interface
legada. O Desktop Head Unit (DHU) aceita sideload e serve para testar a UI CAL.

## Build para teste no DHU

```bash
./gradlew :androidApp:assembleDebug
```

## Build para Google Play (Internal App Sharing)

Use um applicationId próprio para não conflitar com a versão sideload:

```bash
./gradlew :androidApp:assembleRelease \
  -PsimpmusicApplicationId=com.seu.pacote.simpmusic
```

O APK gerado aceita Internal App Sharing. Para AAB, os splits de ABI do projeto
precisam ser desativados, pois o Play rejeita múltiplos arquivos de recursos
encolhidos no `bundleRelease` atual.

## Relação com `custom`

O branch `custom` removeu todo o código CAL e mantém apenas a UI legada,
com grade, capas, ícones e prateleiras otimizados para ficar o mais próximo
possível desta interface. Este branch é um arquivo vivo para consulta e para
publicação futura na Play Store; ele não é mesclado em `custom`.
