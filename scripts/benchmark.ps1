param(
    [Parameter(ValueFromRemainingArguments = $true)]
    $RemainingArgs
)

& "$PSScriptRoot\benchmark-local.ps1" @RemainingArgs
