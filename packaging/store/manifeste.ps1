# Écrit le manifeste MSIX final à partir du modèle, de la version du pom.xml et de store.properties.
# Appelé par store.bat :  manifeste.ps1 -Version 2.4 -Sortie target\store\package\AppxManifest.xml
param(
    [Parameter(Mandatory)] [string] $Version,
    [Parameter(Mandatory)] [string] $Sortie
)
$ErrorActionPreference = 'Stop'
$ici = Split-Path -Parent $MyInvocation.MyCommand.Path

# Le Store veut 4 nombres et un dernier nombre à 0 : 2.4 -> 2.4.0.0
$nombres = @(($Version -replace '[^0-9.].*$', '').Split('.') | Where-Object { $_ -ne '' })
while ($nombres.Count -lt 3) { $nombres += '0' }
$versionMsix = (($nombres | Select-Object -First 3) + '0') -join '.'

$props = @{}
foreach ($ligne in [IO.File]::ReadAllLines((Join-Path $ici 'store.properties'))) {
    if ($ligne -match '^\s*([^#=][^=]*?)\s*=\s*(.*?)\s*$') { $props[$Matches[1]] = $Matches[2] }
}
foreach ($cle in 'identity.name', 'publisher', 'publisher.display') {
    if (-not $props[$cle]) { throw "store.properties : la valeur '$cle' manque" }
}
if (($props.Values -join ' ') -match 'A-REMPLIR') {
    Write-Warning "store.properties contient encore A-REMPLIR : paquet de TEST uniquement (refuse par le Store)."
}

function Xml([string] $s) { [Security.SecurityElement]::Escape($s) }

$xml = [IO.File]::ReadAllText((Join-Path $ici 'AppxManifest.xml'))
$xml = $xml.Replace('@VERSION@', $versionMsix)
$xml = $xml.Replace('@IDENTITY_NAME@', (Xml $props['identity.name']))
$xml = $xml.Replace('@PUBLISHER_DISPLAY@', (Xml $props['publisher.display']))
$xml = $xml.Replace('@PUBLISHER@', (Xml $props['publisher']))
[IO.File]::WriteAllText($Sortie, $xml, (New-Object Text.UTF8Encoding $false))
Write-Host "Manifeste ecrit (version $versionMsix)"
