# CraftTudo 3.0.0

Plugin Paper 26.3 para fabricação em massa. A interface usa inventário padrão Bukkit/Paper e pode ser exibida a clientes Bedrock por Geyser/Floodgate.

## Recursos
- /crafttudo, /craftall e /craftmax abrem o menu.
- Por padrão, só lista receitas normais que podem ser montadas com o inventário atual.
- Alternância para visualizar todas as receitas suportadas.
- Filtros por categoria BUILDING, EQUIPMENT, REDSTONE e MISC.
- Pesquisa com /crafttudo buscar <texto> e limpeza com /crafttudo limpar.
- Fabricação repetida até faltar ingrediente, espaço para o resultado/sobras ou atingir o limite configurado.
- Usa RecipeChoice.test para alternativas e escolhas exatas.
- Usa Bukkit.getCraftingRecipe e Bukkit.craftItemResult do Paper para validar a receita e obter resultado/sobras.
- Simula alterações numa cópia do inventário antes de aplicar; não joga excedentes no chão.
- Bloqueia cliques de movimentação e arraste enquanto o menu está aberto.
- /crafttudo recarregar atualiza receitas e configurações (permissão crafttudo.admin).

## Compatibilidade
- Paper API 26.3 e Java 25.
- Interface de inventário padrão do Bukkit/Paper. Geyser/Floodgate traduzem esse tipo de inventário; é necessário testar nas versões Bedrock usadas no servidor.

## Limites conhecidos
- Suporta receitas normais ShapedRecipe e ShapelessRecipe.
- Receitas especiais com lógica imperativa/dinâmica não são listadas como fabricáveis.
- Plugins que alteram receitas/resultados por eventos podem alterar o resultado usado.
- A compilação não substitui o teste num Paper 26.3 real nem a validação em cliente Bedrock.

## Configuração
plugins/CraftTudo/config.yml:
- max-crafts-per-click: máximo de ciclos por clique (padrão 4096, limite 16384).
- show-all-recipes-by-default: false para mostrar somente receitas possíveis no inventário.
