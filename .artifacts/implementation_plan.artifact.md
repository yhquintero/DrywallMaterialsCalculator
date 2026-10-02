# Modernization of Application Icons

Improve the visual identity of the three applications (`:app`, `:keygen`, `:cleaner`) by creating professional, high-quality adaptive icons using Android VectorDrawables.

## Proposed Icon Designs

### 1. Drywall Materials Calculator (`:app`)
- **Visual**: A stylized drywall corner with a modern calculator overlay.
- **Theme**: Professional Construction / Architecture.
- **Colors**: Deep Blue (#0061A4) and Light Gray (#F0F0F0).
- **Foreground**: A high-detail calculator and a drywall spatula forming a "V" shape for "Verification/Value".

### 2. Keygen Pro YHQuintero (`:keygen`)
- **Visual**: A golden cryptographic key inside a security badge.
- **Theme**: Security / Administration.
- **Colors**: Golden Yellow (#FFD700) and Midnight Blue (#0D1B2A).
- **Foreground**: A complex key with a "chip" pattern and the letter "P" (Pro) integrated into the handle.

### 3. Trial Cleaner (`:cleaner`)
- **Visual**: A stylized broom or magic wand resetting a "7-day" clock.
- **Theme**: Maintenance / Utility.
- **Colors**: Vibrant Orange (#FF9800) and Emerald Green (#2ECC71).
- **Foreground**: A circular arrow (Reset) combined with a clean sparkle effect.

## Proposed Changes

### Module: `:app`
#### [MODIFY] [ic_launcher_foreground.xml](file:///D:/Proyectos/DrywallMaterialsCalculator/app/src/main/res/drawable/ic_launcher_foreground.xml)
- Complete redesign with a more detailed and professional vector composition.

### Module: `:keygen`
#### [MODIFY] [ic_launcher_foreground.xml](file:///D:/Proyectos/DrywallMaterialsCalculator/keygen/src/main/res/drawable/ic_launcher_foreground.xml)
- Update to a more "Premium" look with better shading and digital motifs.

### Module: `:cleaner` [NEW RESOURCES]
#### [NEW] [ic_launcher_foreground.xml](file:///D:/Proyectos/DrywallMaterialsCalculator/cleaner/src/main/res/drawable/ic_launcher_foreground.xml)
#### [NEW] [ic_launcher_background.xml](file:///D:/Proyectos/DrywallMaterialsCalculator/cleaner/src/main/res/drawable/ic_launcher_background.xml)
#### [NEW] [ic_launcher.xml](file:///D:/Proyectos/DrywallMaterialsCalculator/cleaner/src/main/res/mipmap-anydpi-v26/ic_launcher.xml)
#### [NEW] [ic_launcher_round.xml](file:///D:/Proyectos/DrywallMaterialsCalculator/cleaner/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml)
- Create the full resource structure for adaptive icons in the newly created module.

## Verification Plan

### Manual Verification
- Deploy each app and verify the icon appears correctly on the home screen.
- Check icon consistency in the app drawer.
- Build release APKs for all 3 and inspect the icon resources.
