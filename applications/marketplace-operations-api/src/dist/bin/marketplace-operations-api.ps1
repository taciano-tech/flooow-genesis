$ErrorActionPreference = 'Stop'

$distributionRoot = Split-Path -Parent $PSScriptRoot
$classpath = Join-Path $distributionRoot 'lib\*'

& java -cp $classpath 'io.flooow.marketplace.api.ApplicationKt'
exit $LASTEXITCODE
