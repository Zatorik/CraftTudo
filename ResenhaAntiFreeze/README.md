# ResenhaAntiFreeze 1.0.0

Plugin de diagnostico para Paper 26.3, pensado para investigar congelamentos visuais relatados por jogadores Bedrock durante PvP.

## O que registra
- Ping do jogador e TPS recente do servidor.
- Frequencia de golpes causados e recebidos durante a janela de combate.
- Projeteis lancados e entidades, projeteis e TNT proximos.
- Quantidade de efeitos de pocao ativos.
- Explosoes proximas durante o combate.
- Identificacao Bedrock opcional atraves do Floodgate.

## Comandos
- `/antifreeze status`: status do proprio jogador.
- `/antifreeze status <jogador>`: status de outro jogador (admin).
- `/antifreeze report`: ultimas amostras (admin).
- `/antifreeze reload`: recarrega config (admin).

Permissao administrativa: `resenhaantifreeze.admin` (op por padrao).

## Arquivos
- `plugins/ResenhaAntiFreeze/config.yml`
- `plugins/ResenhaAntiFreeze/diagnostics.log`

## Limites importantes
Esta primeira versao e somente diagnostica. Paper nao expoe, por si so, uma medicao completa dos pacotes efetivamente renderizados pelo cliente Bedrock nem consegue corrigir travamentos do renderizador do celular. O plugin registra indicadores do servidor para separar lag de servidor/rede de um possivel gargalo no cliente. Nenhuma particula, animacao, dano ou regra de combate e alterada.

A deteccao Bedrock depende do Floodgate estar instalado. Sem Floodgate, o plugin mostra Java/nao identificado.
