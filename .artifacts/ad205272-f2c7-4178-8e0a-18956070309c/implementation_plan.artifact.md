# Implementation Plan - Fix Fatal Exception: No se puede tener stock negativo

The application crashes with an `IllegalArgumentException` when updating material stock, because of a strict validation check in `MaterialRepository` that doesn't account for floating-point precision errors or unexpected states.

## User Review Required

> [!IMPORTANT]
> I am relaxing the strict stock validation to allow for tiny negative values (caused by floating-point precision) and preventing the app from crashing by adding error handling in the ViewModels.

## Proposed Changes

### Data Layer

#### [MODIFY] [MaterialRepository.kt](file:///D:/Proyectos/DrywallMaterialsCalculator/app/src/main/java/com/drywall/calculator/data/repository/MaterialRepository.kt)
- Modify `updateStock` to use a small epsilon ($10^{-9}$) when checking for negative stock.
- This prevents precision errors like $0.3 - 0.1 - 0.2 \approx -2.7 \times 10^{-17}$ from triggering the crash.

#### [MODIFY] [InventoryRepository.kt](file:///D:/Proyectos/DrywallMaterialsCalculator/app/src/main/java/com/drywall/calculator/data/repository/InventoryRepository.kt)
- Update `deleteTransaction` and `updateTransactionQuantity` to use the same epsilon for consistency.

### Presentation Layer

#### [MODIFY] [InventoryViewModel.kt](file:///D:/Proyectos/DrywallMaterialsCalculator/app/src/main/java/com/drywall/calculator/presentation/ui/inventory/InventoryViewModel.kt)
- Add `try-catch` blocks to `addStock`, `consumeStock`, `deleteTransaction`, `updateTransactionReason`, and `updateTransactionQuantity` to prevent fatal crashes and potentially log/notify the UI if something goes wrong.

#### [MODIFY] [InventoryUpdateViewModel.kt](file:///D:/Proyectos/DrywallMaterialsCalculator/app/src/main/java/com/drywall/calculator/presentation/ui/inventory/InventoryUpdateViewModel.kt) (If it exists)
- I should check if there's an update view model for transactions.

## Verification Plan

### Automated Tests
- Run existing unit tests (if any) to ensure no regressions.

### Manual Verification
- Attempt to perform an inventory movement that results in zero stock (e.g., add 0.3, consume 0.1, then consume 0.2).
- Verify the app no longer crashes.
