# cg.ps1 - Codebase Graph CLI helper
# Usage: .\cg.ps1 <command> [args]

param(
    [Parameter(Position=0)]
    [string]$Command,
    
    [Parameter(Position=1, ValueFromRemainingArguments=$true)]
    [string[]]$Args
)

$container = "codebase-graph-indexer"
$neo4jArgs = "--neo4j-uri bolt://neo4j:7687 --neo4j-password codebase123"

switch ($Command) {
    "ls" {
        docker exec $container ls /projects
    }
    "index" {
        $project = $Args[0]
        $name = if ($Args[1]) { $Args[1] } else { $project }
        Write-Host "Indexing /projects/$project as '$name'..." -ForegroundColor Cyan
        docker exec $container java -jar /app/codebase-graph.jar index "/projects/$project" --name $name $neo4jArgs -v
    }
    "list" {
        docker exec $container java -jar /app/codebase-graph.jar list-indices
    }
    "status" {
        docker exec $container java -jar /app/codebase-graph.jar status $neo4jArgs
    }
    "shell" {
        docker exec -it $container bash
    }
    default {
        Write-Host "Codebase Graph CLI" -ForegroundColor Green
        Write-Host ""
        Write-Host "Usage: .\cg.ps1 <command> [args]" -ForegroundColor Yellow
        Write-Host ""
        Write-Host "Commands:"
        Write-Host "  ls                     List projects in /projects"
        Write-Host "  index <project> [name] Index a project"
        Write-Host "  list                   List stored indices"
        Write-Host "  status                 Check Neo4j status"
        Write-Host "  shell                  Open bash in container"
        Write-Host ""
        Write-Host "Examples:"
        Write-Host "  .\cg.ps1 ls"
        Write-Host "  .\cg.ps1 index keycloak-example-demo/admin/admin keycloak-admin"
        Write-Host "  .\cg.ps1 index simple-java-demo"
    }
}
