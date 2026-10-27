# Como usar o DebugGuard 1.4.5

Aparelho: Galaxy S25. Pacote: `dev.satin.debugguard`.

## Como você abre a CAIXA

A CAIXA está **oculta** (sem ícone). Abertura habitual: **Settings → Apps → CAIXA → Open**. A rotina Samsung “App opened” ainda dispara o Perfil seguro. Ao sair, voltar ao **Settings** ou ao **launcher** deve encerrar a sessão pausada.

## O que a rotina faz agora

Ao abrir **Gov.br / CAIXA / FGTS**:

1. Pausa de verdade: `development_settings_enabled`, `adb_enabled` e `adb_wifi_enabled` → `0`
2. Reabre o app bancário (a Action da Samsung cancela a biometria; o relançamento devolve a tela)
3. Quando **Settings/launcher/outro app** fica mais recente que o banco (UsageStats) **ou** você toca em **Restaurar**, o ADB volta (~4 s após detectar a saída)

## Notificações (1.4.5)

- Canais **silenciosos** (sem som/vibração), importância mínima.
- A notificação extra “ADB pausado” fica **desligada** por padrão (o Guard já tem aviso mínimo + Restaurar).
- No app: interruptor **Notificação extra de pausa**.
- Para sumir da barra: Configurações → Apps → DebugGuard → Notificações → canal do Guard.

**1.4.4:** UsageStats `lastTimeVisible` decide saída (Settings conta como saída). Release de rollback: `v1.4.4`.

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
