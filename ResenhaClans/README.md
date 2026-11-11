# ResenhaClans 1.2.0

Plugin de clans e qualidade de vida social para Paper 26.3 / Java 25.

## Clans
- Persistência em YAML com gravação atômica.
- Criar clan com tag exclusiva (3-5 caracteres), nome e descrição.
- Até 20 membros por padrão (configurável).
- Cargos: Líder, Colíder, Oficial e Membro.
- Convites temporários com aceitar/recusar.
- Expulsar, promover/rebaixar, transferir liderança e dissolver com confirmação.
- Chat de clan fixo ou temporário.
- Base com teleporte atrasado, cancelável por movimento/dano (configurável).
- Alianças com pedido/aceite e proteção de fogo amigo.
- Guerras entre clans, anúncios e pontos por eliminações entre clans em guerra.
- Fogo amigo configurável.
- Estatísticas de kills/mortes individuais e por membro.
- Ranking, lista, informações e menu clicável.
- Tag pequena ✦ [TAG] ao lado do nome na lista de jogadores, configurável.

## Cores de tag (1.2.0)
- `/clan cor` abre uma paleta visual com 16 cores.
- Apenas o líder pode alterar a cor do clan.
- A cor é salva em `clans.yml` por clan; clans existentes recebem cobre automaticamente.
- A cor aparece na tag do TAB e no prefixo do chat do clan.

## Chat e social QoL
- /clanchat alterna para chat exclusivo do clan.
- /clanchat on|off define o modo explicitamente.
- /g volta imediatamente ao chat global.
- /msg <jogador> <mensagem> envia mensagem privada.
- /tell, /w e /whisper também são interceptados para aplicar os filtros do plugin.
- /r <mensagem> ou /reply <mensagem> responde à última conversa privada.
- /ignore <jogador> bloqueia mensagens globais, de clan e privadas desse jogador.
- /unignore <jogador> remove o bloqueio.
- /ignorelist lista os bloqueios.
- /chat abre um menu de preferências.
- /chat toggle <opção> alterna uma preferência; /chat toggle <opção> on|off define explicitamente o estado.

## Preferências individuais (salvas em clans.yml)
- Chat global.
- Chat de clan.
- Mensagens privadas.
- Avisos de entrada/saída do clan.
- Convites de clan.
- Diplomacia/alianças.
- Guerras e pontos.
- Anúncios.

Todas as preferências começam ativadas. Cada jogador pode desligar apenas o que não quer receber.

## Comandos principais
- /clan criar <TAG> <nome>
- /clan menu
- /clan info [TAG], /clan membros, /clan list, /clan top
- /clan convidar <jogador>, /clan aceitar [TAG], /clan recusar [TAG]
- /clan expulsar <jogador>, /clan promover <jogador>, /clan rebaixar <jogador>
- /clan transferir <jogador>, /clan sair, /clan dissolver confirm
- /clan desc <texto>, /clan anunciar <mensagem>
- /clan sethome, /clan home
- /clan friendlyfire <on|off>
- /clan ally <TAG>, /clan allyaccept <TAG>, /clan unally <TAG>, /clan allies
- /clan guerra <TAG>, /clan peace <TAG>
- /clan cor • personalizar a cor da tag (líder)
- /clan stats [jogador], /clan reload

## Instalação e dados
- Paper 26.3 / Java 25.
- Server-side; não exige mod nos clientes. Menus usam inventários padrão, compatíveis com clientes Bedrock via Geyser em princípio.
- Dados persistidos em plugins/ResenhaClans/clans.yml.
- Faça backup de clans.yml antes de atualizar.
- A compilação não substitui teste funcional em servidor real.

## Próximas ideias
Proteção territorial, banco de clan, baús compartilhados, temporadas, recompensas, torneios, histórico de mensagens e logs administrativos.
