# CarryOnPaper 1.1.1

Plugin server-side para Paper 26.3, Java 25.

## Captura de mobs
A captura usa uma allowlist fechada de tipos. O plugin não usa nem aceita ovos de invocação como mecanismo de captura. Exige Shift + botão direito com as duas mãos vazias.

Mobs passivos autorizados:
- Allay, tatu, axolote, morcego, camelo
- Gato, galinha, bacalhau, vaca, burro
- Raposa, sapo, lula-brilhante, cavalo, coguvaca
- Mula, jaguatirica, papagaio, porco, coelho
- Salmão, ovelha, farejador, lula, strider
- Girino, peixe tropical, tartaruga, aldeão e comerciante ambulante

Mobs neutros ou que podem atacar jogadores quando provocados (como panda, lobo, abelha, cabra e urso-polar) ficam de fora. Mobs hostis também ficam de fora.

Mobs aquáticos só podem ser capturados na água e só são soltos dentro de uma coluna de água segura. Se não houver água por perto, o plugin mantém o mob protegido em vez de o soltar em terra.

## Blocos
Lista fechada:
- Baú simples e baú com armadilha (baús duplos são recusados)
- Barril
- Forno, alto-forno e defumador
- Ejetor, liberador e funil
- Caixas de shulker em todas as cores

O plugin recusa blocos abertos, trancados ou com loot table aleatória. Guarda inventário, BlockData/orientação, nome personalizado e PDC. Para funis, guarda cooldown. Para fornos, guarda combustível restante, progresso de cozimento, tempo total, velocidade de cozimento e contagem de receitas usadas para preservar a experiência pendente.

## Controles
- Shift + botão direito com as duas mãos vazias em um mob permitido ou bloco permitido: pegar.
- Botão direito em uma superfície: soltar/colocar.
- Apenas um mob ou bloco por jogador.
- Enquanto carrega bloco, cliques de inventário, arrastar, soltar e trocar mão são bloqueados para proteger o item de transporte.
- Mobs ficam sem IA e invulneráveis durante o transporte; as propriedades originais são restauradas ao soltar.
- Ao desconectar, morrer, teletransportar ou desligar o plugin, o plugin tenta restaurar o mob em um local adequado.
- Mobs transportados que voltam a carregar em chunks posteriormente também passam por recuperação automática.
- Itens de transporte usam tags PDC e ficam no inventário do jogador até colocação confirmada.

## Compatibilidade e validação
- Feito para Paper 26.3 com Java 25.
- Como o plugin roda no servidor, não exige mod nos clientes.
- Geyser/Bedrock precisa ser validado em teste real; eventos de clique podem variar conforme a versão do Geyser.
- Compilação bem-sucedida não substitui teste no servidor.

## Instalação
Coloque CarryOnPaper-1.1.1.jar na pasta plugins/ e reinicie o servidor.
Permissão carryonpaper.use (padrão: todos).
