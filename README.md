# DebugGuard

App local para usar Gov.br / CAIXA / FGTS no Galaxy S25 **sem desligar** a depuração USB/Wi‑Fi nem perder as animações em `0,5×`.

Versão atual: **1.4.4** (`versionCode` 14). Tema `DeviceDefault.DayNight` (claro/escuro do sistema).

## O que faz

| Ação | Efeito |
| --- | --- |
| **Aplicar perfil seguro** | `development_settings_enabled=0`, `adb_enabled=1`, `adb_wifi_enabled=1`, animações `0,5×` |
| **Pausar depurações** | Desliga indicador + USB + Wi‑Fi ADB (salva os valores anteriores) |
| **Restaurar** | Devolve os três valores salvos na pausa |

A ação **Perfil seguro** (atalho das Rotinas) **pausa o ADB** ao abrir Gov.br / CAIXA / FGTS, reabre o app, e o **Guard** restaura o ADB quando o banco sai de foco (ou pela notificação Restaurar). A CAIXA só passa com ADB off — isso já foi validado no aparelho.

## Uso diário (recomendado)

1. DebugGuard instalado com as permissões (inclui `GET_USAGE_STATS`).
2. Rotina **App opened**: Gov.br, CAIXA, FGTS → Perfil seguro.
3. Abra o banco, use a biometria; ao fechar, o ADB volta sozinho.

Detalhes: [docs/USO.md](docs/USO.md).

## Build e testes

```bash
bash scripts/test.sh
bash scripts/build.sh          # gera build/debugguard.apk
# opcional:
VERSION_NAME=1.0.1 VERSION_CODE=4 bash scripts/build.sh
```

Instalação no aparelho:

```bash
adb install -r build/debugguard.apk
adb shell pm grant --user 0 dev.satin.debugguard android.permission.WRITE_SECURE_SETTINGS
adb shell pm grant --user 0 dev.satin.debugguard android.permission.POST_NOTIFICATIONS
adb shell appops set --user 0 dev.satin.debugguard GET_USAGE_STATS allow
```

## Comparação com o Geto 1.17.3

O Geto (F-Droid, GPLv3) é um gerenciador genérico de `Settings.*` por app, com Compose, Room e atalhos que aplicam templates e abrem o alvo. A revisão em `../revisao-geto.md` aponta falhas: templates revertem o indicador para `1` fixo, aplicação parcial sem rollback completo e sem restauração automática ao sair do app.

**O que trouxemos só como ideia (código próprio):** painel com valores ao vivo, ação principal de perfil seguro e status da permissão.

**O que não trouxemos:** stack Compose/Hilt/Room, lista de apps, templates genéricos, fluxo “aplicar e abrir app” do Geto (no S25 a rotina Samsung cobre isso melhor).

Não copiamos código do Geto.

## Limites

- `WRITE_SECURE_SETTINGS` é ampla e só entra via ADB.
- `ApplySafeProfileActivity` é exportada com allowlist (Rotinas, shell, systemui, o próprio app). A abertura cancela a biometria; a 1.2.0 relança o app bancário em seguida.
- Se o indicador já estiver em `1` no instante da abertura, o app pode ter checado antes da rotina; o boot + perfil em `0` no dia a dia reduz essa janela.
