# Sends a test OTP-style email through spring.mail defaults (localhost:1025)
# and verifies delivery via the Mailpit REST API.

$ErrorActionPreference = "Stop"
$smtpHost = "localhost"
$smtpPort = 1025
$mailpitApi = "http://localhost:8025/api/v1"
$to = "smtp-test@parknova.local"
$from = "noreply@parknova.io"
$otp = Get-Random -Minimum 100000 -Maximum 999999
$subject = "ParkNova email verification code"
$body = "Your ParkNova email verification code is: $otp`n`nThis code expires in 10 minutes."

Write-Host "Sending test mail to $to via ${smtpHost}:${smtpPort} ..."
$smtp = New-Object System.Net.Mail.SmtpClient($smtpHost, $smtpPort)
$smtp.EnableSsl = $false
$msg = New-Object System.Net.Mail.MailMessage($from, $to, $subject, $body)
try {
    $smtp.Send($msg)
} finally {
    $msg.Dispose()
    $smtp.Dispose()
}
Write-Host "SMTP send OK."

Start-Sleep -Milliseconds 500
$messages = Invoke-RestMethod -Uri "$mailpitApi/messages"
if ($messages.total -lt 1) {
    Write-Error "Mailpit has no messages - SMTP may not be wired to Mailpit."
}

$latestId = $messages.messages[0].ID
$latest = Invoke-RestMethod -Uri "$mailpitApi/message/$latestId"
$latestSubject = $latest.Subject
$latestText = $latest.Text

Write-Host "Mailpit received: subject='$latestSubject'"
if ($latestSubject -ne $subject -or $latestText -notmatch [regex]::Escape("$otp")) {
    Write-Error "Latest Mailpit message did not match the test OTP email."
}

Write-Host "SMTP verified. OTP $otp visible in Mailpit UI: http://localhost:8025"
Write-Host "Open message id=$latestId"
