# Como usar o DebugGuard 1.4.7

Aparelho: Galaxy S25. Pacote: `dev.satin.debugguard`.

## Como você abre a CAIXA

A CAIXA está **oculta** (sem ícone). Abertura habitual: **Settings → Apps → CAIXA → Open**. A rotina Samsung “App opened” ainda dispara o Perfil seguro. Ao sair, voltar ao **Settings** ou ao **launcher** deve encerrar a sessão pausada.

## O que a rotina faz agora

Ao abrir **Gov.br / CAIXA / FGTS**:

1. Pausa de verdade: `development_settings_enabled`, `adb_enabled` e `adb_wifi_enabled` → `0`
2. Reabre o app bancário (a Action da Samsung cancela a biometria; o relançamento devolve a tela)
3. Quando **Settings/launcher** fica na frente **e** esse evento é recente (últimos ~20 s) **ou** você toca em **Restaurar**, o ADB volta (~10 s de confirmação)

## Notificações (1.4.8)

Toggles no app (padrão: tudo off — painel limpo):

- **Mostrar no painel (perfil seguro)** — aviso contínuo no dia a dia
- **Mostrar durante pausa ADB** — aviso + Restaurar enquanto o banco está pausado
- **Notificação extra de pausa** — segunda notificação
- **Som / vibração** — canais com som
- Botão para **configurações de notificação do sistema**

Com os toggles off, o Guard remove a entrada do painel após o start obrigatório do Android.

**1.4.5:** empate launcher/CAIXA (~6 ms) → restore no meio da sessão.

**1.4.6:** margem fixa de 5 s → sessão ok, mas restore podia ficar preso (>2 min) até Restaurar.

**1.4.7:** saída = foreign **fresco** (≤20 s) e na frente (≥1,5 s). Blip do trampoline envelhece e para de contar; voltar ao Settings conta. Rollback: `v1.4.4`.

## Instalação

```bash
adb install -r build/debugguard.apk
adb shell pm grant --user 0 dev.satin.debugguard android.permission.WRITE_SECURE_SETTINGS
adb shell pm grant --user 0 dev.satin.debugguard android.permission.POST_NOTIFICATIONS
adb shell appops set --user 0 dev.satin.debugguard GET_USAGE_STATS allow
```

Abra o DebugGuard uma vez. Rotina Samsung **App opened** → ação **Perfil seguro** (mesmo atalho; o comportamento agora é pausar ADB).

## Botões no app

- **Aplicar perfil seguro** — só indicador `0`, ADB continua ligado (uso manual / dia a dia sem banco).
- **Pausar depurações** — mesma pausa da rotina, na mão.
- **Restaurar** — religa ADB com os valores salvos.

## Notas

- Enquanto o banco estiver aberto, o Wi‑Fi debugging cai — esperado.
- Restauração automática usa UsageStats; se falhar, use a notificação **Restaurar**.
- Shell de emergência: `adb shell am start -a dev.satin.debugguard.action.RESTORE -n dev.satin.debugguard/.ApplySafeProfileActivity` (quando o ADB já tiver voltado por outro meio).
