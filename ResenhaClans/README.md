# ResenhaClans 1.0.0

Plugin de clans para Paper 26.3 / Java 25. Desenvolvido para o Pura-Resenha.

## Recursos implementados
- Persistência em YAML com gravação atômica em plugins/ResenhaClans/clans.yml.
- Criar clan com tag exclusiva (3-5 letras/números), nome e descrição.
- Até 20 membros por padrão, limite configurável.
- Cargos: Líder, Colíder, Oficial e Membro.
- Convites com expiração configurável, aceitar e recusar.
- Expulsar membros, promover/rebaixar (líder), transferir liderança e dissolver com confirmação.
- Chat privado alternável com /clan chat e mensagem direta /clan c.
- Base com /clan sethome e /clan home; atraso configurável e cancelamento ao mover ou receber dano.
- Alianças com pedido e aceite. Aliados não podem causar dano uns aos outros.
- Guerras entre clans, anúncios globais e pontos por eliminações entre os clans em guerra.
- Fogo amigo configurável por clan.
- Estatísticas persistentes de kills/mortes individuais e por membro.
- Ranking de clans, lista, informações e menu clicável.
- Proteção de comandos com cargos e permissões.
- Compatível com clientes Java e Bedrock por ser server-side; o menu usa inventário padrão.

## Comandos
- /clan criar <TAG> <nome>
- /clan menu
- /clan info [TAG], /clan membros, /clan list, /clan top
- /clan convidar <jogador>, /clan aceitar [TAG], /clan recusar [TAG]
- /clan expulsar <jogador>, /clan promover <jogador>, /clan rebaixar <jogador>
- /clan transferir <jogador>, /clan sair, /clan dissolver confirm
- /clan desc <texto>, /clan anunciar <mensagem>
- /clan sethome, /clan home
- /clan chat, /clan c <mensagem>
- /clan friendlyfire <on|off>
- /clan ally <TAG>, /clan allyaccept <TAG>, /clan unally <TAG>, /clan allies
- /clan guerra <TAG>, /clan peace <TAG>
- /clan stats [jogador]
- /clan reload (OP)

## Observações
- O armazenamento é YAML e é salvo a cada alteração. Faça backup de clans.yml antes de reinstalar ou atualizar.
- Pontos de clan são obtidos por eliminações de jogadores pertencentes a clans em guerra.
- Guerras não criam arenas nem regiões de batalha; o PVP normal do servidor continua ativo.
- Proteção territorial, banco de clan, baús compartilhados, temporadas, recompensas, torneios e logs administrativos são ideias para versões futuras.
