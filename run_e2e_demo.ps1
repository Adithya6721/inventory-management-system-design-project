# Real supplier integration checks, with isolated databases and owned test processes.
python (Join-Path $PSScriptRoot 'scripts/test-supplier.py')
exit $LASTEXITCODE
