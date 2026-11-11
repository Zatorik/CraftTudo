# CarryOnPaper 1.0.0

Plugin server-side para Paper 26.3, Java 25.

## Captura de mobs
A captura usa uma allowlist fechada de EntityType. O plugin não interpreta ovos de spawn como comando e exige as duas mãos vazias. Mobs fora da lista são recusados.

Permitidos:
- Allay, tatu, morcego, camelo
- Gato, galinha, vaca, burro, raposa, sapo
- Cavalo, coguvaca, mula, jaguatirica, papagaio
- Porco, coelho, ovelha, farejador, strider, tartaruga
- Aldeão e comerciante ambulante

Mobs aquáticos que precisam permanecer na água e mobs neutros/hostis ficam fora da lista.

## Blocos
Lista fechada:
- Baú simples e baú com armadilha (baús duplos são recusados)
- Barril
- Ejetor, liberador e funil
- Caixas de shulker em todas as cores

O plugin recusa blocos abertos, trancados ou com loot table aleatória. Guarda inventário, BlockData/orientação, nome personalizado, PDC do bloco e cooldown do funil. Não apaga o bloco original até conseguir criar o item de transporte.

## Controles
- Agachar + botão direito com as duas mãos vazias em um mob permitido ou bloco permitido: pegar.
- Botão direito em uma superfície: soltar/colocar.
- Apenas um mob ou bloco por jogador.
- Enquanto carrega bloco, o inventário fica bloqueado para evitar mover/duplicar o item de transporte.
- O mob fica invulnerável e sem IA enquanto carregado; as propriedades anteriores são restauradas ao soltar.
- Ao desconectar, morrer, teletransportar ou desligar o plugin, o mob é solto e restaurado.
- O item de bloco transportado mantém os dados no PDC e sobrevive a reinícios limpos.

## Instalação
Coloque CarryOnPaper-1.0.0.jar na pasta plugins/ e reinicie o servidor.
Permissão carryonpaper.use (padrão: todos).
