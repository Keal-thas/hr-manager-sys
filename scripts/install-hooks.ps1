$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)
git config core.hooksPath .githooks
git config core.whitespace cr-at-eol
Write-Host "git hooks installed: core.hooksPath=.githooks"
