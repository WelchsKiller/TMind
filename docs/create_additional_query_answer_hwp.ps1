$ErrorActionPreference = 'Stop'
$contentPath = Join-Path $PSScriptRoot 'additional_query_answer_content.txt'
$pathFile = Join-Path $PSScriptRoot 'additional_output_path.txt'
$outPath = (Get-Content -LiteralPath $pathFile -Encoding UTF8 -TotalCount 1).Trim()
$tempPath = Join-Path $PSScriptRoot 'additional_query_answer.hwp'

function Add-HwpLine {
    param([string]$Text)
    $hwp.HAction.GetDefault('InsertText', $hwp.HParameterSet.HInsertText.HSet)
    $hwp.HParameterSet.HInsertText.Text = ($Text + "`r`n")
    [void]$hwp.HAction.Execute('InsertText', $hwp.HParameterSet.HInsertText.HSet)
}

$lines = Get-Content -LiteralPath $contentPath -Encoding UTF8

$hwp = New-Object -ComObject HWPFrame.HwpObject
$hwp.RegisterModule('FilePathCheckDLL', 'FilePathCheckerModuleExample')
$hwp.XHwpWindows.Item(0).Visible = $false

$hwp.HAction.GetDefault('FileNew', $hwp.HParameterSet.HFileOpenSave.HSet)
[void]$hwp.HAction.Execute('FileNew', $hwp.HParameterSet.HFileOpenSave.HSet)

foreach ($line in $lines) {
    Add-HwpLine $line
}

$hwp.HAction.GetDefault('FileSaveAs_S', $hwp.HParameterSet.HFileOpenSave.HSet)
$hwp.HParameterSet.HFileOpenSave.filename = $tempPath
$hwp.HParameterSet.HFileOpenSave.Format = 'HWP'
[void]$hwp.HAction.Execute('FileSaveAs_S', $hwp.HParameterSet.HFileOpenSave.HSet)
$hwp.Quit()

if (-not (Test-Path -LiteralPath $tempPath)) {
    throw "HWP save failed: $tempPath"
}

Copy-Item -LiteralPath $tempPath -Destination $outPath -Force
Write-Output ("Created: " + $outPath + " (" + (Get-Item -LiteralPath $outPath).Length + " bytes)")
