# Verificar as alterações
git diff -- docs/vision/KERNEL-CONCEPT-GRAPH.md

# Verificar problemas de whitespace
git diff --check

# Adicionar ao staging
git add docs/vision/KERNEL-CONCEPT-GRAPH.md

# Confirmar o staging
git status

# Criar o commit
git commit -m "docs(vision): introduce kernel ontology"

# Enviar para o GitHub
git push