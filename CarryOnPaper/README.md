# CarryOnPaper 1.4.2

Plugin server-side para Paper 26.3, Java 25.

## Captura de mobs
A captura usa uma allowlist fechada de tipos. Exige Shift + botão direito com as duas mãos vazias.

Mobs passivos autorizados:
- Allay, tatu, axolote, morcego, camelo
- Gato, galinha, bacalhau, vaca, burro
- Raposa, sapo, lula-brilhante, cavalo, coguvaca
- Mula, jaguatirica, papagaio, porco, coelho
- Salmão, ovelha, farejador, lula, strider
- Girino, peixe tropical, tartaruga, aldeão e comerciante ambulante

Mobs neutros ou que podem atacar quando provocados e mobs hostis ficam de fora.

Mobs aquáticos só podem ser capturados na água e só são soltos dentro de uma coluna de água segura.

## Preservação de dados dos mobs
- Salva e restaura mão principal, mão secundária e armaduras de entidades vivas.
- Também preserva estado geral dos animais: idade, bloqueio de idade, tempo de reprodução, causa da reprodução, domesticação, dono, postura sentada e, para ovelhas, cor e estado tosquiado.
- Salva e restaura o inventário de cavalos, burros, mulas e lhamas, incluindo sela, armadura, decoração e baús quando expostos pelo inventário da montaria.
- Para porcos e striders, salva o estado da sela e os tempos de impulso.
- Para aldeões, salva profissão, tipo, nível, experiência, contador diário de reposições, ofertas comerciais, inventário, reputações por jogador e memórias de estação de trabalho, ponto de encontro e casa quando disponíveis.
- A cópia fica tanto nos dados persistentes da entidade quanto em `plugins/CarryOnPaper/mob-backups.yml`, para não depender exclusivamente do PDC. O arquivo externo é gravado por substituição atômica para reduzir o risco de corrupção por gravação interrompida.
- A cópia externa é removida somente depois da restauração dos dados e das propriedades originais do mob.
- Se a restauração ou a movimentação falhar, o mob continua marcado e protegido para nova tentativa, em vez de apagar a cópia.
- A remoção do backup externo só é confirmada depois que a gravação do arquivo atualizado termina com sucesso; se falhar, o backup é recolocado em memória e a entidade permanece marcada.
- Se o PDC da entidade perder a snapshot, o plugin tenta recuperá-la pelo UUID usando o arquivo externo quando a entidade voltar a carregar.

## Blocos
Lista fechada:
- Baú simples e baú com armadilha (baús duplos são recusados)
- Barril
- Forno, alto-forno e defumador
- Ejetor, liberador e funil
- Caixas de shulker em todas as cores

O plugin recusa blocos abertos, trancados ou com loot table aleatória. Guarda inventário, BlockData/orientação, nome personalizado e PDC. Para funis, guarda cooldown. Para fornos, guarda combustível restante, progresso de cozimento, tempo total, velocidade de cozimento e contagem de receitas usadas.

## Controles e recuperação
- Shift + botão direito com as duas mãos vazias em um mob permitido ou bloco permitido: pegar.
- Botão direito em uma superfície: soltar/colocar.
- Apenas um mob ou bloco por jogador.
- Mobs ficam sem IA e invulneráveis durante o transporte; as propriedades originais são restauradas ao soltar.
- Ao desconectar, morrer, teletransportar ou desligar o plugin, o plugin tenta restaurar o mob em um local adequado.
- Mobs transportados que voltam a carregar em chunks posteriormente também passam por recuperação automática.

## Compatibilidade e validação
- Feito para Paper 26.3 com Java 25.
- Não exige mod nos clientes.
- Geyser/Bedrock precisa ser validado em teste real.
- A compilação valida compatibilidade de código, mas não substitui testes reais de comportamento no Minecraft. A cópia externa protege os dados enquanto a entidade ainda puder ser localizada pelo UUID. Ela não recria automaticamente uma entidade que tenha sido permanentemente removida do mundo, pois isso poderia gerar duplicatas quando um chunk estivesse apenas descarregado. Esta versão ainda precisa de teste controlado para confirmar o comportamento em transporte, desconexão e reinício.

## Instalação
Coloque CarryOnPaper-1.4.2.jar na pasta plugins/ e reinicie o servidor.

Permissão: carryonpaper.use (padrão: todos).
