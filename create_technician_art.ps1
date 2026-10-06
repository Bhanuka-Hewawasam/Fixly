Add-Type -AssemblyName System.Drawing

$size = 256
$bmp = New-Object System.Drawing.Bitmap($size, $size)
$g = [System.Drawing.Graphics]::FromImage($bmp)

$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

# Clear background transparent
$g.Clear([System.Drawing.Color]::Transparent)

# 1. Background Circular Glow
$bgBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(40, 255, 255, 255))
$g.FillEllipse($bgBrush, 8, 8, 240, 240)
$bgBrush.Dispose()

# White Ring Outline
$ringPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(180, 255, 255, 255), 4)
$g.DrawEllipse($ringPen, 12, 12, 232, 232)
$ringPen.Dispose()

# 2. Worker Body (Blue Jumpsuit)
$bodyBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 25, 80, 220))
$g.FillEllipse($bodyBrush, 48, 140, 160, 110)
$bodyBrush.Dispose()

# Vest / Collar detail
$collarBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 245, 158, 11))
$g.FillPolygon($collarBrush, @(
    (New-Object System.Drawing.Point(100, 150)),
    (New-Object System.Drawing.Point(128, 185)),
    (New-Object System.Drawing.Point(156, 150)),
    (New-Object System.Drawing.Point(128, 150))
))
$collarBrush.Dispose()

# 3. Head & Neck
$skinBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 255, 205, 178))
# Neck
$g.FillRectangle($skinBrush, 116, 120, 24, 30)
# Face
$g.FillEllipse($skinBrush, 96, 70, 64, 64)
# Ears
$g.FillEllipse($skinBrush, 90, 88, 14, 20)
$g.FillEllipse($skinBrush, 152, 88, 14, 20)
$skinBrush.Dispose()

# Eyes & Friendly Smile
$eyeBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 30, 41, 59))
$g.FillEllipse($eyeBrush, 112, 92, 8, 10)
$g.FillEllipse($eyeBrush, 136, 92, 8, 10)
$eyeBrush.Dispose()

# Smile Arc
$smilePen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 180, 83, 9), 3)
$g.DrawArc($smilePen, 116, 102, 24, 18, 20, 140)
$smilePen.Dispose()

# 4. Safety Hardhat / Cap (Amber/Yellow)
$capBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 245, 158, 11))
# Dome
$g.FillPie($capBrush, 86, 42, 84, 70, 180, 180)
# Visor/Brim
$g.FillRectangle($capBrush, 80, 72, 96, 12)
$capBrush.Dispose()

# Hardhat Badge (Fixly Blue)
$badgeBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 18, 85, 245))
$g.FillEllipse($badgeBrush, 120, 52, 16, 16)
$badgeBrush.Dispose()

# 5. Wrench Tool in Hand
$wrenchPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 226, 232, 240), 10)
$wrenchPen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$wrenchPen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
$g.DrawLine($wrenchPen, 60, 120, 95, 155)
$wrenchPen.Dispose()

# Wrench Head
$headBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 203, 213, 225))
$g.FillEllipse($headBrush, 48, 108, 24, 24)
$g.FillEllipse([System.Drawing.Brushes]::Transparent, 54, 114, 12, 12)
$headBrush.Dispose()

# 6. Quality Verified Star Badge (Bottom Right)
$starBg = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 16, 185, 129))
$g.FillEllipse($starBg, 168, 152, 48, 48)
$starBg.Dispose()

# Checkmark
$checkPen = New-Object System.Drawing.Pen([System.Drawing.Color]::White, 4)
$checkPen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$checkPen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
$g.DrawLines($checkPen, @(
    (New-Object System.Drawing.Point(180, 176)),
    (New-Object System.Drawing.Point(188, 186)),
    (New-Object System.Drawing.Point(204, 166))
))
$checkPen.Dispose()

$g.Dispose()

$outPath = "app/src/main/res/drawable/banner_technician.png"
$bmp.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()

Write-Output "Created banner_technician.png successfully"
