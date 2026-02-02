param(
    [Parameter(Mandatory = $true)]
    [string]$Path,
    [string]$Name = ""
)

# 1. Validate Path
if (-not (Test-Path $Path)) {
    Write-Error "❌ Path not found: $Path"
    exit 1
}
$AbsPath = Resolve-Path $Path
$AbsPathString = $AbsPath.Path

# 2. Determine Project Name
if ($Name -eq "") {
    $Name = Split-Path $AbsPathString -Leaf
}

Write-Host "🚀 Starting Indexing for: $Name"
Write-Host "📂 Source Path: $AbsPathString"

# 4. Run Docker Command
docker run --rm --network codebase-graph_default `
    -v "${AbsPathString}:/repo" `
    codebase-graph:latest `
    index /repo --name "$Name" `
    --neo4j-uri bolt://neo4j:7687 `
    --neo4j-password codebase123

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "✅ Indexing Successfully Completed!"
    Write-Host "📊 Check stats: http://localhost:8080/stats"
    Write-Host "🔎 Query symbols: http://localhost:8080/symbols?name=YourClassName"
}
else {
    Write-Host ""
    Write-Host "❌ Indexing Failed. Please check the error messages above."
    Write-Host "👉 Ensure the server is running: docker-compose -f docker-compose.full.yml up -d"
}
