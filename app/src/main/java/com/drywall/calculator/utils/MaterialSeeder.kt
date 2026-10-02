package com.drywall.calculator.utils

import com.drywall.calculator.data.local.entity.Material

object MaterialSeeder {
    fun getDefaultMaterials(): List<Material> {
        val materials = mutableListOf<Material>()
        
        // Placas de Yeso (10)
        materials.add(createMaterial("Placa ST 12.5mm 1.2x2.4", 12.0, "unidad"))
        materials.add(createMaterial("Placa ST 9.5mm 1.2x2.4", 10.5, "unidad"))
        materials.add(createMaterial("Placa RH (Humedad) 12.5mm", 16.0, "unidad"))
        materials.add(createMaterial("Placa RF (Fuego) 12.5mm", 18.0, "unidad"))
        materials.add(createMaterial("Placa ST 15mm 1.2x2.4", 14.0, "unidad"))
        materials.add(createMaterial("Placa RH 15mm 1.2x2.4", 20.0, "unidad"))
        materials.add(createMaterial("Placa RF 15mm 1.2x2.4", 22.0, "unidad"))
        materials.add(createMaterial("Placa Extrabura 12.5mm", 25.0, "unidad"))
        materials.add(createMaterial("Placa Acústica 12.5mm", 28.0, "unidad"))
        materials.add(createMaterial("Placa para Cielos 7mm", 9.0, "unidad"))

        // Perfiles (25)
        val profiles = listOf("Canal", "Montante", "Omega", "Ángulo Perimetral", "Vigueta")
        val sizes = listOf("34mm", "60mm", "70mm", "90mm", "100mm")
        for (p in profiles) {
            for (s in sizes) {
                materials.add(createMaterial("$p $s x 3m", 5.0, "unidad"))
            }
        }

        // Tornillos (15)
        materials.add(createMaterial("Tornillo T1 punta aguja x100", 2.0, "caja"))
        materials.add(createMaterial("Tornillo T1 punta mecha x100", 2.5, "caja"))
        materials.add(createMaterial("Tornillo T2 punta aguja x100", 3.0, "caja"))
        materials.add(createMaterial("Tornillo T2 punta mecha x100", 3.5, "caja"))
        materials.add(createMaterial("Tornillo T3 punta aguja x100", 4.0, "caja"))
        materials.add(createMaterial("Tornillo T4 punta aguja x100", 5.0, "caja"))
        materials.add(createMaterial("Tornillo Drywall 1\" x500", 8.0, "caja"))
        materials.add(createMaterial("Tornillo Drywall 1 1/4\" x500", 10.0, "caja"))
        materials.add(createMaterial("Tornillo Drywall 1 5/8\" x500", 12.0, "caja"))
        materials.add(createMaterial("Tornillo Drywall 2\" x500", 15.0, "caja"))
        materials.add(createMaterial("Tornillo Wafer 1/2\" x100", 4.5, "caja"))
        materials.add(createMaterial("Tarugo N6 con tornillo x100", 6.0, "caja"))
        materials.add(createMaterial("Tarugo N8 con tornillo x100", 8.0, "caja"))
        materials.add(createMaterial("Fijación para Techo x100", 15.0, "caja"))
        materials.add(createMaterial("Clavo de acero 1\" x100", 5.0, "caja"))

        // Masillas y Pegamentos (10)
        materials.add(createMaterial("Masilla Ready Mix 25kg", 20.0, "balde"))
        materials.add(createMaterial("Masilla Ready Mix 5kg", 6.0, "balde"))
        materials.add(createMaterial("Masilla de Secado Rápido 30m", 12.0, "bolsa"))
        materials.add(createMaterial("Masilla de Secado Rápido 90m", 12.0, "bolsa"))
        materials.add(createMaterial("Compuesto para Juntas 20kg", 18.0, "balde"))
        materials.add(createMaterial("Adhesivo para Placas 25kg", 15.0, "bolsa"))
        materials.add(createMaterial("Sellador Acústico 300ml", 7.0, "tubo"))
        materials.add(createMaterial("Sellador de Silicona 300ml", 5.0, "tubo"))
        materials.add(createMaterial("Cola Vinílica 1kg", 4.0, "pote"))
        materials.add(createMaterial("Espuma de Poliuretano 750ml", 10.0, "tubo"))

        // Cintas (10)
        materials.add(createMaterial("Cinta de Papel 50m", 3.0, "rollo"))
        materials.add(createMaterial("Cinta de Papel 150m", 8.0, "rollo"))
        materials.add(createMaterial("Cinta Tramada (Malla) 45m", 5.0, "rollo"))
        materials.add(createMaterial("Cinta Tramada (Malla) 90m", 9.0, "rollo"))
        materials.add(createMaterial("Cinta Flex-Corner 30m", 15.0, "rollo"))
        materials.add(createMaterial("Cinta Metálica 30m", 12.0, "rollo"))
        materials.add(createMaterial("Cinta de Enmascarar 24mm", 2.0, "rollo"))
        materials.add(createMaterial("Cinta de Enmascarar 48mm", 4.0, "rollo"))
        materials.add(createMaterial("Cinta Doble Faz 10m", 6.0, "rollo"))
        materials.add(createMaterial("Cinta de Butilo 15m", 18.0, "rollo"))

        // Aislantes (10)
        materials.add(createMaterial("Lana de Vidrio 50mm c/papel", 35.0, "rollo"))
        materials.add(createMaterial("Lana de Vidrio 50mm s/papel", 30.0, "rollo"))
        materials.add(createMaterial("Lana de Vidrio 100mm", 50.0, "rollo"))
        materials.add(createMaterial("Lana de Roca 50mm", 45.0, "paquete"))
        materials.add(createMaterial("Lana de Roca 75mm", 60.0, "paquete"))
        materials.add(createMaterial("EPS (Icopor) 20mm 1x1", 2.0, "plancha"))
        materials.add(createMaterial("EPS (Icopor) 50mm 1x1", 5.0, "plancha"))
        materials.add(createMaterial("Barrera de Vapor 200mic", 25.0, "rollo"))
        materials.add(createMaterial("Membrana Hidrófuga", 40.0, "rollo"))
        materials.add(createMaterial("Banda Acústica 50mm 20m", 10.0, "rollo"))

        // Accesorios (15)
        materials.add(createMaterial("Esquinero Metálico 2.6m", 3.0, "unidad"))
        materials.add(createMaterial("Esquinero Plástico 2.6m", 2.5, "unidad"))
        materials.add(createMaterial("Junta de Dilatación 3m", 15.0, "unidad"))
        materials.add(createMaterial("Perfil Buña Z 3m", 8.0, "unidad"))
        materials.add(createMaterial("Perfil U de terminación", 5.0, "unidad"))
        materials.add(createMaterial("Caja Eléctrica para Drywall", 1.5, "unidad"))
        materials.add(createMaterial("Soporte para Cargas Pesadas", 10.0, "unidad"))
        materials.add(createMaterial("Tapa de Inspección 20x20", 20.0, "unidad"))
        materials.add(createMaterial("Tapa de Inspección 40x40", 35.0, "unidad"))
        materials.add(createMaterial("Tapa de Inspección 60x60", 50.0, "unidad"))
        materials.add(createMaterial("Varilla Roscada 1/4\" 1m", 4.0, "unidad"))
        materials.add(createMaterial("Tuerca 1/4\" x100", 5.0, "caja"))
        materials.add(createMaterial("Arandela 1/4\" x100", 3.0, "caja"))
        materials.add(createMaterial("Anclaje Metálico x50", 25.0, "caja"))
        materials.add(createMaterial("Grampa Omega x100", 12.0, "caja"))

        // Herramientas y Otros (15)
        materials.add(createMaterial("Hoja de Cutter x10", 3.0, "caja"))
        materials.add(createMaterial("Lija para Drywall #80 x10", 5.0, "pack"))
        materials.add(createMaterial("Lija para Drywall #120 x10", 5.0, "pack"))
        materials.add(createMaterial("Lija para Drywall #180 x10", 5.0, "pack"))
        materials.add(createMaterial("Pintura Látex Interior 20L", 60.0, "balde"))
        materials.add(createMaterial("Fijador Sellador 4L", 15.0, "balde"))
        materials.add(createMaterial("Enduido Plástico 10L", 25.0, "balde"))
        materials.add(createMaterial("Rodillo Antigota 22cm", 8.0, "unidad"))
        materials.add(createMaterial("Pincel 2\"", 4.0, "unidad"))
        materials.add(createMaterial("Espátula 6\"", 6.0, "unidad"))
        materials.add(createMaterial("Espátula 10\"", 10.0, "unidad"))
        materials.add(createMaterial("Plato para Masilla", 15.0, "unidad"))
        materials.add(createMaterial("Mecha para Metal 3mm", 2.0, "unidad"))
        materials.add(createMaterial("Punta Ph2 para Atornillador", 1.5, "unidad"))
        materials.add(createMaterial("Guantes de Trabajo", 5.0, "par"))

        return materials
    }

    private fun createMaterial(name: String, purchasePrice: Double, unit: String): Material {
        val profit = AppConfigConstants.DEFAULT_PROFIT
        val salePrice = purchasePrice * (1 + profit / 100)
        return Material(
            name = name,
            quantity = 0.0,
            purchasePrice = purchasePrice,
            salePrice = salePrice,
            profitPercentage = profit,
            unitType = unit
        )
    }
}
