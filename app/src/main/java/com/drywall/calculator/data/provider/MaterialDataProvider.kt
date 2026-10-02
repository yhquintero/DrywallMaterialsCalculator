package com.drywall.calculator.data.provider

import com.drywall.calculator.domain.measure.*

object MaterialDataProvider {

    // ──────────────────────────────────────────────
    // ALL MEASURES
    // ──────────────────────────────────────────────
    val allMeasures: List<MaterialMeasure> by lazy {
        plasterboardMeasures + profileMeasures + fastenerMeasures +
            insulationMeasures + tapeMeasures + beadMeasures +
            ceilingGridMeasures + ceilingTileMeasures + compoundMeasures +
            accessoryMeasures + vaporBarrierMeasures
    }

    // ──────────────────────────────────────────────
    // ALL MATERIAL GROUPS
    // ──────────────────────────────────────────────
    val allGroups: List<MaterialGroup> by lazy { listOf(
        plasterboardGroup, profilesGroup, fastenersGroup,
        insulationGroup, compoundsGroup, tapesGroup,
        beadsGroup, ceilingsGroup, accessoriesGroup
    ) }

    // ──────────────────────────────────────────────
    // SEARCH INDEX
    // ──────────────────────────────────────────────
    private val searchIndex: Map<String, List<MaterialMeasure>> by lazy {
        val index = mutableMapOf<String, MutableList<MaterialMeasure>>()
        allMeasures.forEach { measure ->
            val keywords = measure.tags +
                listOf(measure.name, measure.id, measure.materialId) +
                measure.values.map { "${it.dimension}: ${it.formatted()}" }
            keywords.forEach { keyword ->
                val normalized = keyword.lowercase().trim()
                if (normalized.isNotBlank()) {
                    index.getOrPut(normalized) { mutableListOf() }.add(measure)
                }
            }
            index.getOrPut(measure.category.id.lowercase()) { mutableListOf() }.add(measure)
            index.getOrPut(measure.category.displayName.lowercase()) { mutableListOf() }.add(measure)
            index.getOrPut(measure.standard.code.lowercase()) { mutableListOf() }.add(measure)
            index.getOrPut(measure.standard.displayName.lowercase()) { mutableListOf() }.add(measure)
        }
        index
    }

    fun search(query: String): List<MaterialMeasure> {
        if (query.isBlank()) return emptyList()
        val normalized = query.lowercase().trim()
        val results = mutableSetOf<MaterialMeasure>()
        searchIndex[normalized]?.let { results.addAll(it) }
        searchIndex.forEach { (key, measures) ->
            if (key.contains(normalized) || normalized.contains(key)) {
                results.addAll(measures)
            }
        }
        allMeasures.forEach { measure ->
            measure.tags.forEach { tag ->
                val t = tag.lowercase()
                if (t.contains(normalized) || normalized.contains(t)) {
                    results.add(measure)
                }
            }
            if (measure.name.lowercase().contains(normalized)) {
                results.add(measure)
            }
        }
        return results.toList()
    }

    fun filterByCategory(category: MaterialCategory): List<MaterialMeasure> =
        allMeasures.filter { it.category == category }

    fun filterByStandard(region: StandardRegion): List<MaterialMeasure> =
        if (region == StandardRegion.ALL) allMeasures
        else allMeasures.filter { it.standard == region }

    fun filterByCategoryAndStandard(category: MaterialCategory, region: StandardRegion): List<MaterialMeasure> =
        if (region == StandardRegion.ALL) allMeasures.filter { it.category == category }
        else allMeasures.filter { it.category == category && it.standard == region }

    fun getMeasureById(id: String): MaterialMeasure? = allMeasures.find { it.id == id }

    fun getMeasuresByMaterialId(materialId: String): List<MaterialMeasure> =
        allMeasures.filter { it.materialId == materialId }

    fun getMaterialsByCategory(category: MaterialCategory): List<MaterialItem> {
        val group = allGroups.find { it.category == category } ?: return emptyList()
        return group.materials
    }

    // ──────────────────────────────────────────────
    // PLACAS DE YESO
    // ──────────────────────────────────────────────
    private val plasterboardMeasures: List<MaterialMeasure> by lazy {
        val list = mutableListOf<MaterialMeasure>()

        // European Standard (EN 520)
        list += listOf(
            // Standard (A) - 9.5mm
            mm("PLY-E-001","Placa Est\u00e1ndar 600x2400x9.5","PLY-001","A-600-2400-9.5",
                listOf(v("Ancho",600.00),v("Largo",2400.00),v("Espesor",9.50),v("Peso",7.50)),
                tags("placa","est\u00e1ndar","pladur","yeso","600","2400","9.5","muro","techo"),"img_plasterboard_standard"),
            mm("PLY-E-002","Placa Est\u00e1ndar 600x2600x9.5","PLY-001","A-600-2600-9.5",
                listOf(v("Ancho",600.00),v("Largo",2600.00),v("Espesor",9.50),v("Peso",7.50)),
                tags("placa","est\u00e1ndar","600","2600","9.5"),"img_plasterboard_standard"),
            mm("PLY-E-003","Placa Est\u00e1ndar 600x2700x9.5","PLY-001","A-600-2700-9.5",
                listOf(v("Ancho",600.00),v("Largo",2700.00),v("Espesor",9.50),v("Peso",7.50)),
                tags("placa","est\u00e1ndar","600","2700","9.5"),"img_plasterboard_standard"),
            mm("PLY-E-004","Placa Est\u00e1ndar 1200x2400x9.5","PLY-001","A-1200-2400-9.5",
                listOf(v("Ancho",1200.00),v("Largo",2400.00),v("Espesor",9.50),v("Peso",7.50)),
                tags("placa","est\u00e1ndar","1200","2400","9.5","muro","techo"),"img_plasterboard_standard"),
            mm("PLY-E-005","Placa Est\u00e1ndar 1200x2500x9.5","PLY-001","A-1200-2500-9.5",
                listOf(v("Ancho",1200.00),v("Largo",2500.00),v("Espesor",9.50),v("Peso",7.50)),
                tags("placa","est\u00e1ndar","1200","2500","9.5"),"img_plasterboard_standard"),
            mm("PLY-E-006","Placa Est\u00e1ndar 1200x2600x9.5","PLY-001","A-1200-2600-9.5",
                listOf(v("Ancho",1200.00),v("Largo",2600.00),v("Espesor",9.50),v("Peso",7.50)),
                tags("placa","est\u00e1ndar","1200","2600","9.5"),"img_plasterboard_standard"),
            mm("PLY-E-007","Placa Est\u00e1ndar 1200x2700x9.5","PLY-001","A-1200-2700-9.5",
                listOf(v("Ancho",1200.00),v("Largo",2700.00),v("Espesor",9.50),v("Peso",7.50)),
                tags("placa","est\u00e1ndar","1200","2700","9.5"),"img_plasterboard_standard"),
            mm("PLY-E-008","Placa Est\u00e1ndar 1200x2800x9.5","PLY-001","A-1200-2800-9.5",
                listOf(v("Ancho",1200.00),v("Largo",2800.00),v("Espesor",9.50),v("Peso",7.50)),
                tags("placa","est\u00e1ndar","1200","2800","9.5"),"img_plasterboard_standard"),
            mm("PLY-E-009","Placa Est\u00e1ndar 1200x3000x9.5","PLY-001","A-1200-3000-9.5",
                listOf(v("Ancho",1200.00),v("Largo",3000.00),v("Espesor",9.50),v("Peso",7.50)),
                tags("placa","est\u00e1ndar","1200","3000","9.5"),"img_plasterboard_standard"),
            // Standard (A) - 12.5mm
            mm("PLY-E-010","Placa Est\u00e1ndar 1200x2400x12.5","PLY-001","A-1200-2400-12.5",
                listOf(v("Ancho",1200.00),v("Largo",2400.00),v("Espesor",12.50),v("Peso",9.50)),
                tags("placa","est\u00e1ndar","1200","2400","12.5","muro","techo"),"img_plasterboard_standard"),
            mm("PLY-E-011","Placa Est\u00e1ndar 1200x2600x12.5","PLY-001","A-1200-2600-12.5",
                listOf(v("Ancho",1200.00),v("Largo",2600.00),v("Espesor",12.50),v("Peso",9.50)),
                tags("placa","est\u00e1ndar","1200","2600","12.5"),"img_plasterboard_standard"),
            mm("PLY-E-012","Placa Est\u00e1ndar 1200x2700x12.5","PLY-001","A-1200-2700-12.5",
                listOf(v("Ancho",1200.00),v("Largo",2700.00),v("Espesor",12.50),v("Peso",9.50)),
                tags("placa","est\u00e1ndar","1200","2700","12.5"),"img_plasterboard_standard"),
            mm("PLY-E-013","Placa Est\u00e1ndar 1200x2800x12.5","PLY-001","A-1200-2800-12.5",
                listOf(v("Ancho",1200.00),v("Largo",2800.00),v("Espesor",12.50),v("Peso",9.50)),
                tags("placa","est\u00e1ndar","1200","2800","12.5"),"img_plasterboard_standard"),
            mm("PLY-E-014","Placa Est\u00e1ndar 1200x3000x12.5","PLY-001","A-1200-3000-12.5",
                listOf(v("Ancho",1200.00),v("Largo",3000.00),v("Espesor",12.50),v("Peso",9.50)),
                tags("placa","est\u00e1ndar","1200","3000","12.5"),"img_plasterboard_standard"),
            mm("PLY-E-015","Placa Est\u00e1ndar 1200x3600x12.5","PLY-001","A-1200-3600-12.5",
                listOf(v("Ancho",1200.00),v("Largo",3600.00),v("Espesor",12.50),v("Peso",9.50)),
                tags("placa","est\u00e1ndar","1200","3600","12.5"),"img_plasterboard_standard"),
            // Standard (A) - 15mm
            mm("PLY-E-016","Placa Est\u00e1ndar 1200x2400x15","PLY-001","A-1200-2400-15.0",
                listOf(v("Ancho",1200.00),v("Largo",2400.00),v("Espesor",15.00),v("Peso",11.50)),
                tags("placa","est\u00e1ndar","1200","2400","15","gruesa"),"img_plasterboard_standard"),
            mm("PLY-E-017","Placa Est\u00e1ndar 1200x2600x15","PLY-001","A-1200-2600-15.0",
                listOf(v("Ancho",1200.00),v("Largo",2600.00),v("Espesor",15.00),v("Peso",11.50)),
                tags("placa","est\u00e1ndar","1200","2600","15"),"img_plasterboard_standard"),
            mm("PLY-E-018","Placa Est\u00e1ndar 1200x2700x15","PLY-001","A-1200-2700-15.0",
                listOf(v("Ancho",1200.00),v("Largo",2700.00),v("Espesor",15.00),v("Peso",11.50)),
                tags("placa","est\u00e1ndar","1200","2700","15"),"img_plasterboard_standard"),
            mm("PLY-E-019","Placa Est\u00e1ndar 1200x2800x15","PLY-001","A-1200-2800-15.0",
                listOf(v("Ancho",1200.00),v("Largo",2800.00),v("Espesor",15.00),v("Peso",11.50)),
                tags("placa","est\u00e1ndar","1200","2800","15"),"img_plasterboard_standard"),
            mm("PLY-E-020","Placa Est\u00e1ndar 1200x3000x15","PLY-001","A-1200-3000-15.0",
                listOf(v("Ancho",1200.00),v("Largo",3000.00),v("Espesor",15.00),v("Peso",11.50)),
                tags("placa","est\u00e1ndar","1200","3000","15"),"img_plasterboard_standard"),
        )
        // Standard 600x2400x12.5
        list += mm("PLY-E-021","Placa Est\u00e1ndar 600x2400x12.5","PLY-001","A-600-2400-12.5",
            listOf(v("Ancho",600.00),v("Largo",2400.00),v("Espesor",12.50),v("Peso",9.50)),
            tags("placa","est\u00e1ndar","600","2400","12.5"),"img_plasterboard_standard")
        list += mm("PLY-E-022","Placa Est\u00e1ndar 600x2600x12.5","PLY-001","A-600-2600-12.5",
            listOf(v("Ancho",600.00),v("Largo",2600.00),v("Espesor",12.50),v("Peso",9.50)),
            tags("placa","est\u00e1ndar","600","2600","12.5"),"img_plasterboard_standard")

        // RH (H) - 12.5mm
        list += mm("PLY-E-023","Placa RH 1200x2400x12.5","PLY-002","H-1200-2400-12.5",
            listOf(v("Ancho",1200.00),v("Largo",2400.00),v("Espesor",12.50),v("Peso",9.50)),
            tags("placa","rh","humedad","verde","pladur","1200","2400","12.5"),"img_plasterboard_moisture")
        list += mm("PLY-E-024","Placa RH 1200x2600x12.5","PLY-002","H-1200-2600-12.5",
            listOf(v("Ancho",1200.00),v("Largo",2600.00),v("Espesor",12.50),v("Peso",9.50)),
            tags("placa","rh","humedad","verde","1200","2600","12.5"),"img_plasterboard_moisture")
        list += mm("PLY-E-025","Placa RH 1200x2700x12.5","PLY-002","H-1200-2700-12.5",
            listOf(v("Ancho",1200.00),v("Largo",2700.00),v("Espesor",12.50),v("Peso",9.50)),
            tags("placa","rh","humedad","verde","1200","2700","12.5"),"img_plasterboard_moisture")
        list += mm("PLY-E-026","Placa RH 1200x2800x12.5","PLY-002","H-1200-2800-12.5",
            listOf(v("Ancho",1200.00),v("Largo",2800.00),v("Espesor",12.50),v("Peso",9.50)),
            tags("placa","rh","humedad","verde","1200","2800","12.5"),"img_plasterboard_moisture")
        list += mm("PLY-E-027","Placa RH 1200x3000x12.5","PLY-002","H-1200-3000-12.5",
            listOf(v("Ancho",1200.00),v("Largo",3000.00),v("Espesor",12.50),v("Peso",9.50)),
            tags("placa","rh","humedad","verde","1200","3000","12.5"),"img_plasterboard_moisture")
        // RH (H) - 15mm
        list += mm("PLY-E-028","Placa RH 1200x2400x15","PLY-002","H-1200-2400-15.0",
            listOf(v("Ancho",1200.00),v("Largo",2400.00),v("Espesor",15.00),v("Peso",11.50)),
            tags("placa","rh","humedad","verde","1200","2400","15"),"img_plasterboard_moisture")
        list += mm("PLY-E-029","Placa RH 1200x2600x15","PLY-002","H-1200-2600-15.0",
            listOf(v("Ancho",1200.00),v("Largo",2600.00),v("Espesor",15.00),v("Peso",11.50)),
            tags("placa","rh","humedad","verde","1200","2600","15"),"img_plasterboard_moisture")

        // RF (F) - 12.5mm
        list += mm("PLY-E-030","Placa RF 1200x2400x12.5","PLY-003","F-1200-2400-12.5",
            listOf(v("Ancho",1200.00),v("Largo",2400.00),v("Espesor",12.50),v("Peso",10.50)),
            tags("placa","rf","fuego","roja","cortafuego","1200","2400","12.5"),"img_plasterboard_fire")
        list += mm("PLY-E-031","Placa RF 1200x2600x12.5","PLY-003","F-1200-2600-12.5",
            listOf(v("Ancho",1200.00),v("Largo",2600.00),v("Espesor",12.50),v("Peso",10.50)),
            tags("placa","rf","fuego","roja","1200","2600","12.5"),"img_plasterboard_fire")
        list += mm("PLY-E-032","Placa RF 1200x2700x12.5","PLY-003","F-1200-2700-12.5",
            listOf(v("Ancho",1200.00),v("Largo",2700.00),v("Espesor",12.50),v("Peso",10.50)),
            tags("placa","rf","fuego","roja","1200","2700","12.5"),"img_plasterboard_fire")
        list += mm("PLY-E-033","Placa RF 1200x2800x12.5","PLY-003","F-1200-2800-12.5",
            listOf(v("Ancho",1200.00),v("Largo",2800.00),v("Espesor",12.50),v("Peso",10.50)),
            tags("placa","rf","fuego","roja","1200","2800","12.5"),"img_plasterboard_fire")
        list += mm("PLY-E-034","Placa RF 1200x3000x12.5","PLY-003","F-1200-3000-12.5",
            listOf(v("Ancho",1200.00),v("Largo",3000.00),v("Espesor",12.50),v("Peso",10.50)),
            tags("placa","rf","fuego","roja","1200","3000","12.5"),"img_plasterboard_fire")
        // RF (F) - 15mm
        list += mm("PLY-E-035","Placa RF 1200x2400x15","PLY-003","F-1200-2400-15.0",
            listOf(v("Ancho",1200.00),v("Largo",2400.00),v("Espesor",15.00),v("Peso",12.50)),
            tags("placa","rf","fuego","roja","1200","2400","15"),"img_plasterboard_fire")
        list += mm("PLY-E-036","Placa RF 1200x2600x15","PLY-003","F-1200-2600-15.0",
            listOf(v("Ancho",1200.00),v("Largo",2600.00),v("Espesor",15.00),v("Peso",12.50)),
            tags("placa","rf","fuego","roja","1200","2600","15"),"img_plasterboard_fire")

        // RF-RH (FH)
        list += mm("PLY-E-037","Placa RF-RH 1200x2400x12.5","PLY-004","FH-1200-2400-12.5",
            listOf(v("Ancho",1200.00),v("Largo",2400.00),v("Espesor",12.50),v("Peso",10.50)),
            tags("placa","rf-rh","fh","fuego","humedad","rosa","1200","2400","12.5"),"img_plasterboard_fire_moisture")
        list += mm("PLY-E-038","Placa RF-RH 1200x2600x12.5","PLY-004","FH-1200-2600-12.5",
            listOf(v("Ancho",1200.00),v("Largo",2600.00),v("Espesor",12.50),v("Peso",10.50)),
            tags("placa","rf-rh","fh","fuego","humedad","rosa","1200","2600","12.5"),"img_plasterboard_fire_moisture")

        // High Impact
        list += mm("PLY-E-039","Placa Alta Impacto 1200x2400x12.5","PLY-005","HI-1200-2400-12.5",
            listOf(v("Ancho",1200.00),v("Largo",2400.00),v("Espesor",12.50),v("Peso",11.00)),
            tags("placa","impacto","alta","azul","resistencia","1200","2400","12.5"),"img_plasterboard_impact")
        list += mm("PLY-E-040","Placa Alta Impacto 1200x2400x15","PLY-005","HI-1200-2400-15.0",
            listOf(v("Ancho",1200.00),v("Largo",2400.00),v("Espesor",15.00),v("Peso",13.00)),
            tags("placa","impacto","alta","azul","1200","2400","15"),"img_plasterboard_impact")

        // Flexible
        list += mm("PLY-E-041","Placa Flexible 900x2400x6","PLY-007","FLEX-900-2400-6.0",
            listOf(v("Ancho",900.00),v("Largo",2400.00),v("Espesor",6.00),v("Peso",5.00)),
            tags("placa","flexible","curva","arco","900","2400","6","curvatura"),"img_plasterboard_flexible")
        list += mm("PLY-E-042","Placa Flexible 900x2600x6","PLY-007","FLEX-900-2600-6.0",
            listOf(v("Ancho",900.00),v("Largo",2600.00),v("Espesor",6.00),v("Peso",5.00)),
            tags("placa","flexible","curva","900","2600","6","curvatura"),"img_plasterboard_flexible")

        // American Standard (ASTM C36 / GA-216)
        list += mm("PLY-A-001","Regular 48x96x1/2","PLY-001","REG-48-96-0.5",
            listOf(v("Ancho",1219.20),v("Largo",2438.40),v("Espesor",12.70),v("Peso",9.72)),
            tags("placa","regular","americano","astm","48","96","half","inch","drywall"),"img_plasterboard_standard",
            StandardRegion.USA)
        list += mm("PLY-A-002","Regular 48x120x1/2","PLY-001","REG-48-120-0.5",
            listOf(v("Ancho",1219.20),v("Largo",3048.00),v("Espesor",12.70),v("Peso",9.72)),
            tags("placa","regular","americano","astm","48","120","half"),"img_plasterboard_standard",StandardRegion.USA)
        list += mm("PLY-A-003","Regular 48x144x1/2","PLY-001","REG-48-144-0.5",
            listOf(v("Ancho",1219.20),v("Largo",3657.60),v("Espesor",12.70),v("Peso",9.72)),
            tags("placa","regular","americano","astm","48","144","half"),"img_plasterboard_standard",StandardRegion.USA)
        list += mm("PLY-A-004","Regular 54x96x1/2","PLY-001","REG-54-96-0.5",
            listOf(v("Ancho",1371.60),v("Largo",2438.40),v("Espesor",12.70),v("Peso",10.93)),
            tags("placa","regular","americano","54","96","half"),"img_plasterboard_standard",StandardRegion.USA)
        list += mm("PLY-A-006","Regular 48x96x5/8","PLY-001","REG-48-96-0.625",
            listOf(v("Ancho",1219.20),v("Largo",2438.40),v("Espesor",15.88),v("Peso",11.98)),
            tags("placa","regular","americano","48","96","5/8","625"),"img_plasterboard_standard",StandardRegion.USA)
        list += mm("PLY-A-009","Fire Rated 48x96x5/8","PLY-003","FR-48-96-0.625",
            listOf(v("Ancho",1219.20),v("Largo",2438.40),v("Espesor",15.88),v("Peso",12.70)),
            tags("placa","fire","rated","cortafuego","americano","type-x","48","96","5/8"),"img_plasterboard_fire",StandardRegion.USA)
        list += mm("PLY-A-012","Moisture Resistant 48x96x1/2","PLY-002","MR-48-96-0.5",
            listOf(v("Ancho",1219.20),v("Largo",2438.40),v("Espesor",12.70),v("Peso",9.98)),
            tags("placa","moisture","resistant","mr","verde","48","96","half","humedad"),"img_plasterboard_moisture",StandardRegion.USA)

        // Asian Standard (JIS A 6901)
        list += mm("PLY-AS-001","JIS Est\u00e1ndar 910x1820x9","PLY-001","JIS-A-910-1820-9",
            listOf(v("Ancho",910.00),v("Largo",1820.00),v("Espesor",9.00)),
            tags("placa","jis","asi\u00e1tico","japon","910","1820","9","est\u00e1ndar"),"img_plasterboard_standard",StandardRegion.ASIA)
        list += mm("PLY-AS-002","JIS Est\u00e1ndar 910x1820x12","PLY-001","JIS-A-910-1820-12",
            listOf(v("Ancho",910.00),v("Largo",1820.00),v("Espesor",12.00)),
            tags("placa","jis","asi\u00e1tico","japon","910","1820","12"),"img_plasterboard_standard",StandardRegion.ASIA)
        list += mm("PLY-AS-003","JIS Est\u00e1ndar 910x2420x9","PLY-001","JIS-A-910-2420-9",
            listOf(v("Ancho",910.00),v("Largo",2420.00),v("Espesor",9.00)),
            tags("placa","jis","asi\u00e1tico","910","2420","9"),"img_plasterboard_standard",StandardRegion.ASIA)
        list += mm("PLY-AS-004","JIS Est\u00e1ndar 910x2420x12","PLY-001","JIS-A-910-2420-12",
            listOf(v("Ancho",910.00),v("Largo",2420.00),v("Espesor",12.00)),
            tags("placa","jis","asi\u00e1tico","910","2420","12"),"img_plasterboard_standard",StandardRegion.ASIA)
        list += mm("PLY-AS-005","JIS Est\u00e1ndar 910x2730x9","PLY-001","JIS-A-910-2730-9",
            listOf(v("Ancho",910.00),v("Largo",2730.00),v("Espesor",9.00)),
            tags("placa","jis","asi\u00e1tico","910","2730","9"),"img_plasterboard_standard",StandardRegion.ASIA)

        list
    }

    // ──────────────────────────────────────────────
    // PERFILES METALICOS
    // ──────────────────────────────────────────────
    private val profileMeasures: List<MaterialMeasure> by lazy {
        val list = mutableListOf<MaterialMeasure>()

        // Canales (U) / Track
        list += mm("CAN-E-001","Canal 46x30x0.6 L2400","PER-001","CAN-46-30-06",
            listOf(v("Ancho",46.00),v("Alto",30.00),v("Espesor",0.60),v("Largo",2400.00)),
            tags("canal","track","perfil","46","galvanizado","acero"),"img_profile_channel")
        list += mm("CAN-E-002","Canal 46x30x0.6 L2600","PER-001","CAN-46-30-06-L",
            listOf(v("Ancho",46.00),v("Alto",30.00),v("Espesor",0.60),v("Largo",2600.00)),
            tags("canal","track","46","2600"),"img_profile_channel")
        list += mm("CAN-E-003","Canal 46x30x0.6 L3000","PER-001","CAN-46-30-06-XL",
            listOf(v("Ancho",46.00),v("Alto",30.00),v("Espesor",0.60),v("Largo",3000.00)),
            tags("canal","track","46","3000"),"img_profile_channel")
        list += mm("CAN-E-004","Canal 70x30x0.6 L2400","PER-001","CAN-70-30-06",
            listOf(v("Ancho",70.00),v("Alto",30.00),v("Espesor",0.60),v("Largo",2400.00)),
            tags("canal","track","perfil","70","galvanizado","acero","est\u00e1ndar"),"img_profile_channel")
        list += mm("CAN-E-005","Canal 70x30x0.6 L2600","PER-001","CAN-70-30-06-L",
            listOf(v("Ancho",70.00),v("Alto",30.00),v("Espesor",0.60),v("Largo",2600.00)),
            tags("canal","track","70","2600"),"img_profile_channel")
        list += mm("CAN-E-006","Canal 70x30x0.6 L3000","PER-001","CAN-70-30-06-XL",
            listOf(v("Ancho",70.00),v("Alto",30.00),v("Espesor",0.60),v("Largo",3000.00)),
            tags("canal","track","70","3000"),"img_profile_channel")
        list += mm("CAN-E-007","Canal 70x30x0.7 L2400","PER-001","CAN-70-30-07",
            listOf(v("Ancho",70.00),v("Alto",30.00),v("Espesor",0.70),v("Largo",2400.00)),
            tags("canal","track","70","0.7","reforzado"),"img_profile_channel")
        list += mm("CAN-E-009","Canal 70x30x1.0 L2400","PER-001","CAN-70-30-10",
            listOf(v("Ancho",70.00),v("Alto",30.00),v("Espesor",1.00),v("Largo",2400.00)),
            tags("canal","track","70","1.0","pesado"),"img_profile_channel")
        list += mm("CAN-E-010","Canal 90x35x0.6 L2400","PER-001","CAN-90-35-06",
            listOf(v("Ancho",90.00),v("Alto",35.00),v("Espesor",0.60),v("Largo",2400.00)),
            tags("canal","track","90","galvanizado","instalaciones"),"img_profile_channel")
        list += mm("CAN-E-012","Canal 90x35x0.6 L3000","PER-001","CAN-90-35-06-XL",
            listOf(v("Ancho",90.00),v("Alto",35.00),v("Espesor",0.60),v("Largo",3000.00)),
            tags("canal","track","90","3000"),"img_profile_channel")
        list += mm("CAN-E-015","Canal 100x35x0.7 L2400","PER-001","CAN-100-35-07",
            listOf(v("Ancho",100.00),v("Alto",35.00),v("Espesor",0.70),v("Largo",2400.00)),
            tags("canal","track","100","0.7","reforzado"),"img_profile_channel")

        // Montantes (C) / Stud
        list += mm("MON-E-001","Montante 46x46x0.6 L2400","PER-002","MON-46-46-06",
            listOf(v("Ancho",46.00),v("Alma",46.00),v("Espesor",0.60),v("Largo",2400.00)),
            tags("montante","stud","perfil","46","galvanizado","acero","tabique","sencillo"),"img_profile_stud")
        list += mm("MON-E-002","Montante 46x46x0.6 L2600","PER-002","MON-46-46-06-L",
            listOf(v("Ancho",46.00),v("Alma",46.00),v("Espesor",0.60),v("Largo",2600.00)),
            tags("montante","stud","46","2600"),"img_profile_stud")
        list += mm("MON-E-003","Montante 46x46x0.6 L3000","PER-002","MON-46-46-06-XL",
            listOf(v("Ancho",46.00),v("Alma",46.00),v("Espesor",0.60),v("Largo",3000.00)),
            tags("montante","stud","46","3000"),"img_profile_stud")
        list += mm("MON-E-004","Montante 70x46x0.6 L2400","PER-002","MON-70-46-06",
            listOf(v("Ancho",70.00),v("Alma",46.00),v("Espesor",0.60),v("Largo",2400.00)),
            tags("montante","stud","perfil","70","galvanizado","acero","muro","est\u00e1ndar"),"img_profile_stud")
        list += mm("MON-E-005","Montante 70x46x0.6 L2600","PER-002","MON-70-46-06-L",
            listOf(v("Ancho",70.00),v("Alma",46.00),v("Espesor",0.60),v("Largo",2600.00)),
            tags("montante","stud","70","2600"),"img_profile_stud")
        list += mm("MON-E-006","Montante 70x46x0.6 L3000","PER-002","MON-70-46-06-XL",
            listOf(v("Ancho",70.00),v("Alma",46.00),v("Espesor",0.60),v("Largo",3000.00)),
            tags("montante","stud","70","3000"),"img_profile_stud")
        list += mm("MON-E-007","Montante 70x46x0.7 L2400","PER-002","MON-70-46-07",
            listOf(v("Ancho",70.00),v("Alma",46.00),v("Espesor",0.70),v("Largo",2400.00)),
            tags("montante","stud","70","0.7","reforzado"),"img_profile_stud")
        list += mm("MON-E-010","Montante 70x46x1.0 L2400","PER-002","MON-70-46-10",
            listOf(v("Ancho",70.00),v("Alma",46.00),v("Espesor",1.00),v("Largo",2400.00)),
            tags("montante","stud","70","1.0","pesado"),"img_profile_stud")
        list += mm("MON-E-012","Montante 90x46x0.6 L2400","PER-002","MON-90-46-06",
            listOf(v("Ancho",90.00),v("Alma",46.00),v("Espesor",0.60),v("Largo",2400.00)),
            tags("montante","stud","90","galvanizado","instalaciones"),"img_profile_stud")
        list += mm("MON-E-014","Montante 90x46x0.6 L3000","PER-002","MON-90-46-06-XL",
            listOf(v("Ancho",90.00),v("Alma",46.00),v("Espesor",0.60),v("Largo",3000.00)),
            tags("montante","stud","90","3000"),"img_profile_stud")
        list += mm("MON-E-015","Montante 90x46x0.7 L2400","PER-002","MON-90-46-07",
            listOf(v("Ancho",90.00),v("Alma",46.00),v("Espesor",0.70),v("Largo",2400.00)),
            tags("montante","stud","90","0.7","reforzado"),"img_profile_stud")
        list += mm("MON-E-018","Montante 90x46x1.0 L2400","PER-002","MON-90-46-10",
            listOf(v("Ancho",90.00),v("Alma",46.00),v("Espesor",1.00),v("Largo",2400.00)),
            tags("montante","stud","90","1.0","pesado"),"img_profile_stud")
        list += mm("MON-E-020","Montante 100x46x0.7 L2400","PER-002","MON-100-46-07",
            listOf(v("Ancho",100.00),v("Alma",46.00),v("Espesor",0.70),v("Largo",2400.00)),
            tags("montante","stud","100","0.7"),"img_profile_stud")
        list += mm("MON-E-021","Montante 100x46x1.0 L2400","PER-002","MON-100-46-10",
            listOf(v("Ancho",100.00),v("Alma",46.00),v("Espesor",1.00),v("Largo",2400.00)),
            tags("montante","stud","100","1.0","pesado"),"img_profile_stud")

        // Omegas
        list += mm("OMG-E-001","Omega 43x25x0.5 L2400","PER-006","OMG-43-25-05",
            listOf(v("Ancho",43.00),v("Alto",25.00),v("Espesor",0.50),v("Largo",2400.00)),
            tags("omega","techo","falso techo","perfil","43","25"),"img_profile_omega")
        list += mm("OMG-E-002","Omega 43x25x0.6 L2400","PER-006","OMG-43-25-06",
            listOf(v("Ancho",43.00),v("Alto",25.00),v("Espesor",0.60),v("Largo",2400.00)),
            tags("omega","techo","43","25","0.6"),"img_profile_omega")
        list += mm("OMG-E-003","Omega 43x25x0.6 L3000","PER-006","OMG-43-25-06-L",
            listOf(v("Ancho",43.00),v("Alto",25.00),v("Espesor",0.60),v("Largo",3000.00)),
            tags("omega","techo","43","25","3000"),"img_profile_omega")
        list += mm("OMG-E-004","Omega 48x27x0.5 L2400","PER-006","OMG-48-27-05",
            listOf(v("Ancho",48.00),v("Alto",27.00),v("Espesor",0.50),v("Largo",2400.00)),
            tags("omega","techo","48","27"),"img_profile_omega")
        list += mm("OMG-E-005","Omega 48x27x0.6 L2400","PER-006","OMG-48-27-06",
            listOf(v("Ancho",48.00),v("Alto",27.00),v("Espesor",0.60),v("Largo",2400.00)),
            tags("omega","techo","48","27","0.6"),"img_profile_omega")
        list += mm("OMG-E-006","Omega 48x27x0.6 L3000","PER-006","OMG-48-27-06-L",
            listOf(v("Ancho",48.00),v("Alto",27.00),v("Espesor",0.60),v("Largo",3000.00)),
            tags("omega","techo","48","27","3000"),"img_profile_omega")

        // American profiles
        list += mm("PER-A-001","Stud 1-5/8x96 25ga","PER-002","STUD-1625-96-25",
            listOf(v("Ancho",41.28),v("Largo",2438.40),v("Espesor",0.46)),
            tags("stud","americano","1-5/8","25ga","galvanizado"),"img_profile_stud",StandardRegion.USA)
        list += mm("PER-A-002","Track 1-5/8x120 25ga","PER-001","TRK-1625-120-25",
            listOf(v("Ancho",41.28),v("Largo",3048.00),v("Espesor",0.46)),
            tags("track","americano","1-5/8","25ga"),"img_profile_channel",StandardRegion.USA)
        list += mm("PER-A-003","Stud 2-1/2x96 25ga","PER-002","STUD-250-96-25",
            listOf(v("Ancho",63.50),v("Largo",2438.40),v("Espesor",0.46)),
            tags("stud","americano","2-1/2","25ga"),"img_profile_stud",StandardRegion.USA)
        list += mm("PER-A-005","Stud 3-5/8x96 20ga","PER-002","STUD-3625-96-20",
            listOf(v("Ancho",92.08),v("Largo",2438.40),v("Espesor",0.84)),
            tags("stud","americano","3-5/8","20ga","estructural"),"img_profile_stud",StandardRegion.USA)
        list += mm("PER-A-006","Track 3-5/8x120 20ga","PER-001","TRK-3625-120-20",
            listOf(v("Ancho",92.08),v("Largo",3048.00),v("Espesor",0.84)),
            tags("track","americano","3-5/8","20ga"),"img_profile_channel",StandardRegion.USA)
        list += mm("PER-A-013","Stud 6x96 20ga","PER-002","STUD-6-96-20",
            listOf(v("Ancho",152.40),v("Largo",2438.40),v("Espesor",0.84)),
            tags("stud","americano","6","20ga","ancho"),"img_profile_stud",StandardRegion.USA)

        // Steel Framing (PGC/PGU)
        list += mm("STEEL-E-001","PGC 90x43x0.9 L6000","PER-009","PGC-90-43-09",
            listOf(v("Ancho",90.00),v("Alma",43.00),v("Espesor",0.90),v("Largo",6000.00)),
            tags("steel","framing","pgc","estructural","acero","90","0.9"),"img_profile_maestro")
        list += mm("STEEL-E-002","PGC 100x43x0.9 L6000","PER-009","PGC-100-43-09",
            listOf(v("Ancho",100.00),v("Alma",43.00),v("Espesor",0.90),v("Largo",6000.00)),
            tags("steel","framing","pgc","100","0.9"),"img_profile_maestro")
        list += mm("STEEL-E-003","PGC 100x43x1.2 L6000","PER-009","PGC-100-43-12",
            listOf(v("Ancho",100.00),v("Alma",43.00),v("Espesor",1.20),v("Largo",6000.00)),
            tags("steel","framing","pgc","100","1.2","reforzado"),"img_profile_maestro")
        list += mm("STEEL-E-006","PGC 150x43x1.2 L6000","PER-009","PGC-150-43-12",
            listOf(v("Ancho",150.00),v("Alma",43.00),v("Espesor",1.20),v("Largo",6000.00)),
            tags("steel","framing","pgc","150","1.2"),"img_profile_maestro")
        list += mm("STEEL-E-007","PGC 200x43x1.5 L6000","PER-009","PGC-200-43-15",
            listOf(v("Ancho",200.00),v("Alma",43.00),v("Espesor",1.50),v("Largo",6000.00)),
            tags("steel","framing","pgc","200","1.5","pesado"),"img_profile_maestro")

        list
    }

    // ──────────────────────────────────────────────
    // TORNILLERIA
    // ──────────────────────────────────────────────
    private val fastenerMeasures: List<MaterialMeasure> by lazy {
        val list = mutableListOf<MaterialMeasure>()

        // T1 - Sharp point
        list += mm("TRN-E-001","Tornillo T1 3.5x25","TOR-001","T1-35-25",
            listOf(v("Di\u00e1metro",3.50),v("Longitud",25.00)),
            tags("tornillo","t1","comecocos","punta fina","sharp","placa","metal"),"img_screw_t1")
        list += mm("TRN-E-002","Tornillo T1 3.5x32","TOR-001","T1-35-32",
            listOf(v("Di\u00e1metro",3.50),v("Longitud",32.00)),
            tags("tornillo","t1","32"),"img_screw_t1")
        list += mm("TRN-E-003","Tornillo T1 3.5x35","TOR-001","T1-35-35",
            listOf(v("Di\u00e1metro",3.50),v("Longitud",35.00)),
            tags("tornillo","t1","35"),"img_screw_t1")
        list += mm("TRN-E-004","Tornillo T1 3.5x38","TOR-001","T1-35-38",
            listOf(v("Di\u00e1metro",3.50),v("Longitud",38.00)),
            tags("tornillo","t1","38"),"img_screw_t1")
        // T2 - Self-drill
        list += mm("TRN-E-005","Tornillo T2 3.5x25","TOR-002","T2-35-25",
            listOf(v("Di\u00e1metro",3.50),v("Longitud",25.00)),
            tags("tornillo","t2","plancha","broca","self-drill","placa","metal","0.6"),"img_screw_t2")
        list += mm("TRN-E-006","Tornillo T2 3.5x32","TOR-002","T2-35-32",
            listOf(v("Di\u00e1metro",3.50),v("Longitud",32.00)),
            tags("tornillo","t2","32"),"img_screw_t2")
        list += mm("TRN-E-007","Tornillo T2 3.5x35","TOR-002","T2-35-35",
            listOf(v("Di\u00e1metro",3.50),v("Longitud",35.00)),
            tags("tornillo","t2","35"),"img_screw_t2")
        list += mm("TRN-E-008","Tornillo T2 3.5x41","TOR-002","T2-35-41",
            listOf(v("Di\u00e1metro",3.50),v("Longitud",41.00)),
            tags("tornillo","t2","41"),"img_screw_t2")
        list += mm("TRN-E-009","Tornillo T2 3.5x45","TOR-002","T2-35-45",
            listOf(v("Di\u00e1metro",3.50),v("Longitud",45.00)),
            tags("tornillo","t2","45"),"img_screw_t2")
        // T3 - Zincado
        list += mm("TRN-E-010","Tornillo T3 3.5x25","TOR-003","T3-35-25",
            listOf(v("Di\u00e1metro",3.50),v("Longitud",25.00)),
            tags("tornillo","t3","zincado","humedad","exterior"),"img_screw_t3")
        list += mm("TRN-E-011","Tornillo T3 3.5x32","TOR-003","T3-35-32",
            listOf(v("Di\u00e1metro",3.50),v("Longitud",32.00)),
            tags("tornillo","t3","32"),"img_screw_t3")
        list += mm("TRN-E-012","Tornillo T3 3.5x35","TOR-003","T3-35-35",
            listOf(v("Di\u00e1metro",3.50),v("Longitud",35.00)),
            tags("tornillo","t3","35"),"img_screw_t3")
        // T4 - Long
        list += mm("TRN-E-013","Tornillo T4 3.5x51","TOR-004","T4-35-51",
            listOf(v("Di\u00e1metro",3.50),v("Longitud",51.00)),
            tags("tornillo","t4","largo","doble placa","51"),"img_screw_t4")
        list += mm("TRN-E-014","Tornillo T4 3.5x57","TOR-004","T4-35-57",
            listOf(v("Di\u00e1metro",3.50),v("Longitud",57.00)),
            tags("tornillo","t4","57"),"img_screw_t4")
        list += mm("TRN-E-015","Tornillo T4 3.5x63","TOR-004","T4-35-63",
            listOf(v("Di\u00e1metro",3.50),v("Longitud",63.00)),
            tags("tornillo","t4","63"),"img_screw_t4")
        list += mm("TRN-E-016","Tornillo T4 3.5x70","TOR-004","T4-35-70",
            listOf(v("Di\u00e1metro",3.50),v("Longitud",70.00)),
            tags("tornillo","t4","70"),"img_screw_t4")
        // TN - Short
        list += mm("TRN-E-017","Tornillo TN 3.5x22","TOR-005","TN-35-22",
            listOf(v("Di\u00e1metro",3.50),v("Longitud",22.00)),
            tags("tornillo","tn","corto","22","placa simple"),"img_screw_t1")
        list += mm("TRN-E-018","Tornillo TN 3.5x25","TOR-005","TN-35-25",
            listOf(v("Di\u00e1metro",3.50),v("Longitud",25.00)),
            tags("tornillo","tn","25"),"img_screw_t1")
        // Metal-metal screws
        list += mm("TRN-E-019","Tornillo MM 3.5x9.5","TOR-002","MM-35-9",
            listOf(v("Di\u00e1metro",3.50),v("Longitud",9.50)),
            tags("tornillo","mm","metal-metal","broca","9.5","auto-roscante"),"img_screw_t2")
        list += mm("TRN-E-020","Tornillo MM 3.5x11","TOR-002","MM-35-11",
            listOf(v("Di\u00e1metro",3.50),v("Longitud",11.00)),
            tags("tornillo","mm","11"),"img_screw_t2")
        list += mm("TRN-E-021","Tornillo MM 3.5x13","TOR-002","MM-35-13",
            listOf(v("Di\u00e1metro",3.50),v("Longitud",13.00)),
            tags("tornillo","mm","13"),"img_screw_t2")
        list += mm("TRN-E-023","Tornillo MM 4.2x13","TOR-002","MM-42-13",
            listOf(v("Di\u00e1metro",4.20),v("Longitud",13.00)),
            tags("tornillo","mm","4.2","13"),"img_screw_t2")
        list += mm("TRN-E-025","Tornillo MM 4.8x13","TOR-002","MM-48-13",
            listOf(v("Di\u00e1metro",4.80),v("Longitud",13.00)),
            tags("tornillo","mm","4.8","13"),"img_screw_t2")

        // Clavos
        list += mm("CLV-E-001","Clavo Impacto 2.5x25","TOR-006","CIMP-25",
            listOf(v("Di\u00e1metro",2.50),v("Longitud",25.00)),
            tags("clavo","impacto","fulminante","omega","concreto","fijaci\u00f3n"),"img_screw_t1")
        list += mm("CLV-E-002","Clavo Impacto 2.8x32","TOR-006","CIMP-32",
            listOf(v("Di\u00e1metro",2.80),v("Longitud",32.00)),
            tags("clavo","impacto","32"),"img_screw_t1")
        list += mm("CLV-E-004","Clavo Impacto 3.2x45","TOR-006","CIMP-45",
            listOf(v("Di\u00e1metro",3.20),v("Longitud",45.00)),
            tags("clavo","impacto","45","estructural"),"img_screw_t1")

        // Anclajes
        list += mm("ANC-E-001","Anclaje Qu\u00edmico 10x100","TOR-007","AQ-10-100",
            listOf(v("Di\u00e1metro",10.00),v("Longitud",100.00),v("Carga m\u00e1x",500.00)),
            tags("anclaje","qu\u00edmico","inyectable","10","100","carga"),"img_screw_t3")
        list += mm("ANC-E-003","Taco Expansivo 8x50","TOR-007","TEM-8-50",
            listOf(v("Di\u00e1metro",8.00),v("Longitud",50.00),v("Carga m\u00e1x",200.00)),
            tags("anclaje","taco","expansivo","met\u00e1lico","8"),"img_screw_t3")
        list += mm("ANC-E-006","Taco Nylon 6x30","TOR-007","TNY-6-30",
            listOf(v("Di\u00e1metro",6.00),v("Longitud",30.00),v("Carga m\u00e1x",50.00)),
            tags("anclaje","taco","nylon","6","ligero"),"img_screw_t3")
        list += mm("ANC-E-007","Taco Nylon 8x40","TOR-007","TNY-8-40",
            listOf(v("Di\u00e1metro",8.00),v("Longitud",40.00),v("Carga m\u00e1x",80.00)),
            tags("anclaje","taco","nylon","8"),"img_screw_t3")

        list
    }

    // ──────────────────────────────────────────────
    // AISLAMIENTOS
    // ──────────────────────────────────────────────
    private val insulationMeasures: List<MaterialMeasure> by lazy {
        val list = mutableListOf<MaterialMeasure>()

        list += mm("AIS-E-001","Lana Vidrio 40mm","AIS-001","LAV-40",
            listOf(v("Espesor",40.00),v("Ancho",600.00),v("Largo",1200.00),v("R-Valor",0.95)),
            tags("aislamiento","lana","vidrio","mineral","t\u00e9rmico","ac\u00fastico","40mm"),"img_insulation_glasswool")
        list += mm("AIS-E-002","Lana Vidrio 50mm","AIS-001","LAV-50",
            listOf(v("Espesor",50.00),v("Ancho",600.00),v("Largo",1200.00),v("R-Valor",1.20)),
            tags("aislamiento","lana","vidrio","50mm"),"img_insulation_glasswool")
        list += mm("AIS-E-003","Lana Vidrio 60mm","AIS-001","LAV-60",
            listOf(v("Espesor",60.00),v("Ancho",600.00),v("Largo",1200.00),v("R-Valor",1.45)),
            tags("aislamiento","lana","vidrio","60mm"),"img_insulation_glasswool")
        list += mm("AIS-E-004","Lana Vidrio 75mm","AIS-001","LAV-75",
            listOf(v("Espesor",75.00),v("Ancho",600.00),v("Largo",1200.00),v("R-Valor",1.85)),
            tags("aislamiento","lana","vidrio","75mm"),"img_insulation_glasswool")
        list += mm("AIS-E-005","Lana Vidrio 90mm","AIS-001","LAV-90",
            listOf(v("Espesor",90.00),v("Ancho",600.00),v("Largo",1200.00),v("R-Valor",2.25)),
            tags("aislamiento","lana","vidrio","90mm"),"img_insulation_glasswool")
        list += mm("AIS-E-006","Lana Vidrio 100mm","AIS-001","LAV-100",
            listOf(v("Espesor",100.00),v("Ancho",600.00),v("Largo",1200.00),v("R-Valor",2.50)),
            tags("aislamiento","lana","vidrio","100mm"),"img_insulation_glasswool")
        list += mm("AIS-E-007","Lana Vidrio 120mm","AIS-001","LAV-120",
            listOf(v("Espesor",120.00),v("Ancho",600.00),v("Largo",1200.00),v("R-Valor",3.00)),
            tags("aislamiento","lana","vidrio","120mm"),"img_insulation_glasswool")

        // Lana de Roca
        list += mm("AIS-E-008","Lana Roca 40mm","AIS-002","LAR-40",
            listOf(v("Espesor",40.00),v("Ancho",600.00),v("Largo",1000.00)),
            tags("aislamiento","lana","roca","mineral","40mm","ac\u00fastico"),"img_insulation_rockwool")
        list += mm("AIS-E-009","Lana Roca 50mm","AIS-002","LAR-50",
            listOf(v("Espesor",50.00),v("Ancho",600.00),v("Largo",1000.00)),
            tags("aislamiento","lana","roca","50mm"),"img_insulation_rockwool")
        list += mm("AIS-E-010","Lana Roca 80mm","AIS-002","LAR-80",
            listOf(v("Espesor",80.00),v("Ancho",600.00),v("Largo",1000.00)),
            tags("aislamiento","lana","roca","80mm"),"img_insulation_rockwool")

        list
    }

    // ──────────────────────────────────────────────
    // CINTAS
    // ──────────────────────────────────────────────
    private val tapeMeasures: List<MaterialMeasure> by lazy {
        val list = mutableListOf<MaterialMeasure>()

        list += mmm("CIN-E-001","Cinta Papel Perforado 50mmx50m","CIN-001",
            "CPP-50-50",listOf(v("Ancho",50.00),v("Largo",50.00,"largo m"),v("Espesor",0.20)),
            tags("cinta","papel","perforado","junta","masilla","50mm"),"img_tape_paper_perforated")
        list += mmm("CIN-E-002","Cinta Papel Perforado 50mmx90m","CIN-001",
            "CPP-50-90",listOf(v("Ancho",50.00),v("Largo",90.00,"largo m"),v("Espesor",0.20)),
            tags("cinta","papel","perforado","90m"),"img_tape_paper_perforated")
        list += mmm("CIN-E-003","Cinta Papel Perforado 50mmx150m","CIN-001",
            "CPP-50-150",listOf(v("Ancho",50.00),v("Largo",150.00,"largo m"),v("Espesor",0.20)),
            tags("cinta","papel","perforado","150m"),"img_tape_paper_perforated")
        list += mmm("CIN-E-006","Cinta Fibra Vidrio 50mmx50m","CIN-003",
            "CFV-50-50",listOf(v("Ancho",50.00),v("Largo",50.00,"largo m"),v("Espesor",0.30)),
            tags("cinta","fibra","vidrio","malla","50mm","resistente"),"img_tape_fiberglass")
        list += mmm("CIN-E-007","Cinta Fibra Vidrio 50mmx90m","CIN-003",
            "CFV-50-90",listOf(v("Ancho",50.00),v("Largo",90.00,"largo m"),v("Espesor",0.30)),
            tags("cinta","fibra","vidrio","90m"),"img_tape_fiberglass")
        list += mmm("CIN-E-009","Cinta Malla Autoadhesiva 25mmx30m","CIN-004",
            "CMA-25-30",listOf(v("Ancho",25.00),v("Largo",30.00,"largo m"),v("Espesor",1.00)),
            tags("cinta","malla","autoadhesiva","25mm","f\u00e1cil","r\u00e1pida"),"img_tape_mesh")
        list += mmm("CIN-E-011","Cinta Malla Autoadhesiva 50mmx30m","CIN-004",
            "CMA-50-30",listOf(v("Ancho",50.00),v("Largo",30.00,"largo m"),v("Espesor",1.00)),
            tags("cinta","malla","autoadhesiva","50mm"),"img_tape_mesh")
        list += mmm("CIN-E-012","Cinta Malla Autoadhesiva 50mmx90m","CIN-004",
            "CMA-50-90",listOf(v("Ancho",50.00),v("Largo",90.00,"largo m"),v("Espesor",1.00)),
            tags("cinta","malla","autoadhesiva","50mm","90m"),"img_tape_mesh")

        list
    }

    // ──────────────────────────────────────────────
    // ESQUINEROS
    // ──────────────────────────────────────────────
    private val beadMeasures: List<MaterialMeasure> by lazy {
        val list = mutableListOf<MaterialMeasure>()

        list += mm("ESQ-E-001","Esquinero Met\u00e1lico 25x25 L2400","ESQ-001","ESQ-25-25-2400",
            listOf(v("Ala 1",25.00),v("Ala 2",25.00),v("Largo",2400.00)),
            tags("esquinero","cantero","met\u00e1lico","perforado","25mm","esquina","protecci\u00f3n"),"img_bead_metal_perforated")
        list += mm("ESQ-E-002","Esquinero Met\u00e1lico 25x25 L2600","ESQ-001","ESQ-25-25-2600",
            listOf(v("Ala 1",25.00),v("Ala 2",25.00),v("Largo",2600.00)),
            tags("esquinero","met\u00e1lico","25x25","2600"),"img_bead_metal_perforated")
        list += mm("ESQ-E-003","Esquinero Met\u00e1lico 30x30 L2400","ESQ-001","ESQ-30-30-2400",
            listOf(v("Ala 1",30.00),v("Ala 2",30.00),v("Largo",2400.00)),
            tags("esquinero","met\u00e1lico","30mm"),"img_bead_metal_perforated")
        list += mm("ESQ-E-004","Esquinero PVC 25x25 L2400","ESQ-004","PVC-25-25-2400",
            listOf(v("Ala 1",25.00),v("Ala 2",25.00),v("Largo",2400.00)),
            tags("esquinero","pvc","flexible","25mm"),"img_bead_pvc")
        list += mm("ESQ-E-005","Esquinero PVC 30x30 L2400","ESQ-004","PVC-30-30-2400",
            listOf(v("Ala 1",30.00),v("Ala 2",30.00),v("Largo",2400.00)),
            tags("esquinero","pvc","flexible","30mm"),"img_bead_pvc")
        list += mm("ESQ-E-006","Esquinero PVC con Malla 80x80 L2400","ESQ-004","PVC-MALLA-80-2400",
            listOf(v("Ala 1",80.00),v("Ala 2",80.00),v("Largo",2400.00)),
            tags("esquinero","pvc","malla","80mm","esquina","reforzado"),"img_bead_pvc_mesh")
        list += mm("ESQ-E-009","Guardacantos Aluminio 8x10 L2400","ESQ-009","GC-8-10-2400",
            listOf(v("Ala 1",8.00),v("Ala 2",10.00),v("Largo",2400.00)),
            tags("guardacantos","borde","aluminio","protecci\u00f3n"),"img_bead_edge")

        list
    }

    // ──────────────────────────────────────────────
    // TECHOS REGISTRABLES (rejilla)
    // ──────────────────────────────────────────────
    private val ceilingGridMeasures: List<MaterialMeasure> by lazy {
        val list = mutableListOf<MaterialMeasure>()

        list += mm("RET-E-001","Perfil Principal T-24 24x32 L3660","RET-001","T24-MAIN-3660",
            listOf(v("Ala",24.00),v("Alma",32.00),v("Largo",3660.00)),
            tags("techo","reticulado","registrable","t-24","t24","principal","falso techo"),"img_grid_main_t24")
        list += mm("RET-E-002","Perfil Principal T-24 24x32 L3600","RET-001","T24-MAIN-3600",
            listOf(v("Ala",24.00),v("Alma",32.00),v("Largo",3600.00)),
            tags("techo","t-24","principal","3600"),"img_grid_main_t24")
        list += mm("RET-E-003","Perfil Principal T-15 15x32 L3660","RET-001","T15-MAIN-3660",
            listOf(v("Ala",15.00),v("Alma",32.00),v("Largo",3660.00)),
            tags("techo","t-15","t15","principal","visto"),"img_grid_main_t15")
        list += mm("RET-E-004","Perfil Secundario T-24 24x24 L1220","RET-002","T24-CROSS-1220",
            listOf(v("Ala",24.00),v("Alma",24.00),v("Largo",1220.00)),
            tags("techo","secundario","t-24","cruce","1220"),"img_grid_cross_t24")
        list += mm("RET-E-005","Perfil Secundario T-24 24x24 L1200","RET-002","T24-CROSS-1200",
            listOf(v("Ala",24.00),v("Alma",24.00),v("Largo",1200.00)),
            tags("techo","secundario","t-24","1200"),"img_grid_cross_t24")
        list += mm("RET-E-007","Perfil Terciario T-24 24x24 L610","RET-002","T24-TER-610",
            listOf(v("Ala",24.00),v("Alma",24.00),v("Largo",610.00)),
            tags("techo","terciario","tabica","t-24","610","60x60"),"img_grid_cross_t24")
        list += mm("RET-E-009","Angular Perimetral L-24 24x24 L3000","RET-003","L24-3000",
            listOf(v("Ala",24.00),v("Alma",24.00),v("Largo",3000.00)),
            tags("angular","perimetral","l-24","techo","per\u00edmetro"),"img_grid_angle_l24")
        list += mm("RET-E-011","Angular Perimetral L-19 19x19 L3000","RET-003","L19-3000",
            listOf(v("Ala",19.00),v("Alma",19.00),v("Largo",3000.00)),
            tags("angular","perimetral","l-19"),"img_grid_angle_l24")

        list
    }

    // ──────────────────────────────────────────────
    // BALDOSAS CIELO RASO
    // ──────────────────────────────────────────────
    private val ceilingTileMeasures: List<MaterialMeasure> by lazy {
        val list = mutableListOf<MaterialMeasure>()

        list += mm("BAL-E-001","Baldosa Mineral 600x600x12","BAL-001","TILE-MIN-600-12",
            listOf(v("Ancho",600.00),v("Largo",600.00),v("Espesor",12.00)),
            tags("baldosa","cielo raso","mineral","falso techo","600x600","registrable"),"img_tile_mineral_600")
        list += mm("BAL-E-002","Baldosa Mineral 600x600x15","BAL-001","TILE-MIN-600-15",
            listOf(v("Ancho",600.00),v("Largo",600.00),v("Espesor",15.00)),
            tags("baldosa","mineral","600x600","15mm"),"img_tile_mineral_600")
        list += mm("BAL-E-003","Baldosa Mineral 600x1200x12","BAL-001","TILE-MIN-600-1200-12",
            listOf(v("Ancho",600.00),v("Largo",1200.00),v("Espesor",12.00)),
            tags("baldosa","mineral","600x1200","12mm"),"img_tile_mineral_600x1200")
        list += mm("BAL-E-004","Baldosa Metal Microperf 600x600x0.6","BAL-005","TILE-MTL-600-06",
            listOf(v("Ancho",600.00),v("Largo",600.00),v("Espesor",0.60)),
            tags("baldosa","metal","microperforado","acero","600x600"),"img_tile_metal_600")
        list += mm("BAL-E-005","Baldosa Yeso Vinilo 600x600x9","BAL-007","TILE-GYP-600-9",
            listOf(v("Ancho",600.00),v("Largo",600.00),v("Espesor",9.00)),
            tags("baldosa","yeso","vinilo","600x600","lavable"),"img_tile_mineral_600")

        list
    }

    // ──────────────────────────────────────────────
    // MASILLAS
    // ──────────────────────────────────────────────
    private val compoundMeasures: List<MaterialMeasure> by lazy {
        val list = mutableListOf<MaterialMeasure>()

        list += mmm("MAS-E-001","Masilla Juntas Polvo 5kg","MAS-001","MJP-5",
            listOf(v("Peso",5.00,"kg")),
            tags("masilla","juntas","polvo","5kg","yeso"),"img_compound_joint_powder")
        list += mmm("MAS-E-002","Masilla Juntas Polvo 20kg","MAS-001","MJP-20",
            listOf(v("Peso",20.00,"kg")),
            tags("masilla","juntas","polvo","20kg"),"img_compound_joint_powder")
        list += mmm("MAS-E-003","Masilla Juntas Preparada 12L","MAS-002","MJPRE-12",
            listOf(v("Volumen",12.00,"L")),
            tags("masilla","juntas","preparada","12l","cubo"),"img_compound_joint_premixed")
        list += mmm("MAS-E-004","Masilla Juntas Preparada 18L","MAS-002","MJPRE-18",
            listOf(v("Volumen",18.00,"L")),
            tags("masilla","juntas","preparada","18l"),"img_compound_joint_premixed")
        list += mmm("MAS-E-005","Masilla Acabado Polvo 5kg","MAS-003","MAP-5",
            listOf(v("Peso",5.00,"kg")),
            tags("masilla","acabado","polvo","fino","5kg"),"img_compound_finish_powder")
        list += mmm("MAS-E-006","Masilla Acabado Polvo 20kg","MAS-003","MAP-20",
            listOf(v("Peso",20.00,"kg")),
            tags("masilla","acabado","polvo","20kg"),"img_compound_finish_powder")
        list += mmm("MAS-E-007","Masilla RH Humedad 20kg","MAS-005","MRH-20",
            listOf(v("Peso",20.00,"kg")),
            tags("masilla","rh","humedad","20kg","verde"),"img_compound_moisture")
        list += mmm("MAS-E-008","Masilla RF Cortafuego 20kg","MAS-006","MRF-20",
            listOf(v("Peso",20.00,"kg")),
            tags("masilla","rf","fuego","cortafuego","20kg","roja"),"img_compound_fire")

        list
    }

    // ──────────────────────────────────────────────
    // COMPLEMENTOS / ACCESORIOS
    // ──────────────────────────────────────────────
    private val accessoryMeasures: List<MaterialMeasure> by lazy {
        val list = mutableListOf<MaterialMeasure>()

        list += mm("ACC-T-001","Alambre Galvanizado #8 \u23004.06mm","ACC-001","WIRE-8",
            listOf(v("Di\u00e1metro",4.06),v("Calibre","#8")),
            tags("alambre","galvanizado","#8","suspensi\u00f3n","techo","cuelgue"),"img_ceiling_wire_12")
        list += mm("ACC-T-003","Alambre Galvanizado #12 \u23002.76mm","ACC-001","WIRE-12",
            listOf(v("Di\u00e1metro",2.76),v("Calibre","#12")),
            tags("alambre","galvanizado","#12","suspensi\u00f3n","techo"),"img_ceiling_wire_12")
        list += mm("ACC-T-005","Clip Suspensi\u00f3n Regulable","ACC-002","CLIP-ADJ",
            listOf(v("Espesor",0.50)),
            tags("clip","suspensi\u00f3n","regulable","techo","accesorio"),"img_ceiling_clip_adjustable")
        list += mm("ACC-T-007","Cruceta Techo T-24","ACC-003","CROSS-T24",
            listOf(v("Tipo","Pl\u00e1stico/Metal")),
            tags("cruceta","techo","t-24","conexi\u00f3n","cruce"),"img_ceiling_cross_t24")

        list
    }

    // ──────────────────────────────────────────────
    // BARRERAS DE VAPOR
    // ──────────────────────────────────────────────
    private val vaporBarrierMeasures: List<MaterialMeasure> by lazy {
        val list = mutableListOf<MaterialMeasure>()

        list += mmm("BAR-E-001","Film PE 0.15mm 4mx50m","BAR-001","PE-015-4-50",
            listOf(v("Espesor",0.15),v("Ancho",4.00,"m"),v("Largo",50.00,"m")),
            tags("barrera","vapor","film","pe","polietileno","humedad"),"img_vapor_barrier_pe")
        list += mmm("BAR-E-002","Film PE 0.20mm 4mx50m","BAR-001","PE-020-4-50",
            listOf(v("Espesor",0.20),v("Ancho",4.00,"m"),v("Largo",50.00,"m")),
            tags("barrera","vapor","film","pe","0.20"),"img_vapor_barrier_pe")
        list += mmm("BAR-E-003","Membrana Tyvek 0.30mm 1.5mx50m","BAR-003","TYVEK-03-1.5-50",
            listOf(v("Espesor",0.30),v("Ancho",1.50,"m"),v("Largo",50.00,"m")),
            tags("membrana","tyvek","transpirable","exterior","fachada"),"img_weather_barrier")

        list
    }

    // ──────────────────────────────────────────────
    // MATERIAL GROUPS
    // ──────────────────────────────────────────────
    private val plasterboardGroup = MaterialGroup(
        id = "GRP-PLY",
        name = "Placas de Yeso Laminado",
        description = "Placas de yeso laminado (Pladur) para revestimiento de muros, techos y tabiques",
        imageRef = "img_group_plasterboard",
        category = MaterialCategory.PLASTERBOARD,
        materials = listOf(
            MaterialItem("PLY-001","Est\u00e1ndar (A)","Uso general en interiores en seco","Blanco","img_plasterboard_standard",
                getMeasuresByMaterialId("PLY-001")),
            MaterialItem("PLY-002","RH (H)","Resistente a la humedad","Verde","img_plasterboard_moisture",
                getMeasuresByMaterialId("PLY-002")),
            MaterialItem("PLY-003","RF (F)","Resistente al fuego","Rojo","img_plasterboard_fire",
                getMeasuresByMaterialId("PLY-003")),
            MaterialItem("PLY-004","RF-RH (FH)","Fuego + Humedad","Rosa","img_plasterboard_fire_moisture",
                getMeasuresByMaterialId("PLY-004")),
            MaterialItem("PLY-005","Alta Impacto","Resistencia a impactos","Azul","img_plasterboard_impact",
                getMeasuresByMaterialId("PLY-005")),
            MaterialItem("PLY-007","Flexible","Superficies curvas","Blanco curvado","img_plasterboard_flexible",
                getMeasuresByMaterialId("PLY-007")),
        )
    )

    private val profilesGroup = MaterialGroup(
        id = "GRP-PER",
        name = "Perfiles Met\u00e1licos",
        description = "Perfiles de acero galvanizado para estructuras de muros y techos",
        imageRef = "img_group_profiles",
        category = MaterialCategory.PROFILES,
        materials = listOf(
            MaterialItem("PER-001","Canal (U)","Gu\u00eda horizontal en muros (suelo y techo)","","img_profile_channel",
                getMeasuresByMaterialId("PER-001")),
            MaterialItem("PER-002","Montante (C)","Perfil vertical en muros","","img_profile_stud",
                getMeasuresByMaterialId("PER-002")),
            MaterialItem("PER-006","Perfil Omega","Perfil secundario para techos","","img_profile_omega",
                getMeasuresByMaterialId("PER-006")),
            MaterialItem("PER-009","Steel Framing","Perfiles de carga estructural","","img_profile_maestro",
                getMeasuresByMaterialId("PER-009")),
        )
    )

    private val fastenersGroup = MaterialGroup(
        id = "GRP-TOR",
        name = "Torniller\u00eda y Fijaciones",
        description = "Tornillos, clavos, anclajes y fijaciones para sistemas Drywall",
        imageRef = "img_group_fasteners",
        category = MaterialCategory.FASTENERS,
        materials = listOf(
            MaterialItem("TOR-001","T1 (Comecocos)","Fijar placa a perfiles met\u00e1licos finos","","img_screw_t1",
                getMeasuresByMaterialId("TOR-001")),
            MaterialItem("TOR-002","T2 (Plancha/Broca)","Fijar placa a perfiles \u22650.6mm","","img_screw_t2",
                getMeasuresByMaterialId("TOR-002")),
            MaterialItem("TOR-003","T3 (Zincado)","Humedad y exteriores","","img_screw_t3",
                getMeasuresByMaterialId("TOR-003")),
            MaterialItem("TOR-004","T4 (Largo)","Doble placa o placas gruesas","","img_screw_t4",
                getMeasuresByMaterialId("TOR-004")),
            MaterialItem("TOR-005","TN (Corto)","Placa simple 12.5mm a perfil","","img_screw_t1",
                getMeasuresByMaterialId("TOR-005")),
            MaterialItem("TOR-006","Clavos Impacto","Fijaci\u00f3n a concreto","","img_screw_t1",
                getMeasuresByMaterialId("TOR-006")),
            MaterialItem("TOR-007","Anclajes","Anclajes qu\u00edmicos y expansivos","","img_screw_t3",
                getMeasuresByMaterialId("TOR-007")),
        )
    )

    private val insulationGroup = MaterialGroup(
        id = "GRP-AIS",
        name = "Aislamientos",
        description = "Lanas minerales para aislamiento t\u00e9rmico y ac\u00fastico",
        imageRef = "img_group_insulation",
        category = MaterialCategory.INSULATION,
        materials = listOf(
            MaterialItem("AIS-001","Lana de Vidrio","Aislamiento t\u00e9rmico y ac\u00fastico","Amarillo/Verde","img_insulation_glasswool",
                getMeasuresByMaterialId("AIS-001")),
            MaterialItem("AIS-002","Lana de Roca","Aislamiento ac\u00fastico y cortafuego","Marr\u00f3n/Gris","img_insulation_rockwool",
                getMeasuresByMaterialId("AIS-002")),
        )
    )

    private val compoundsGroup = MaterialGroup(
        id = "GRP-MAS",
        name = "Masillas y Pastas",
        description = "Masillas de juntas, acabados y compuestos",
        imageRef = "img_group_compounds",
        category = MaterialCategory.COMPOUNDS,
        materials = listOf(
            MaterialItem("MAS-001","Masilla Juntas (Polvo)","Para juntas entre placas","Blanco","img_compound_joint_powder",
                getMeasuresByMaterialId("MAS-001")),
            MaterialItem("MAS-002","Masilla Juntas (Preparada)","Lista para usar","Blanco","img_compound_joint_premixed",
                getMeasuresByMaterialId("MAS-002")),
            MaterialItem("MAS-003","Masilla Acabado (Polvo)","Capa fina de acabado","Blanco","img_compound_finish_powder",
                getMeasuresByMaterialId("MAS-003")),
            MaterialItem("MAS-005","Masilla RH","Resistente a la humedad","Verde","img_compound_moisture",
                getMeasuresByMaterialId("MAS-005")),
            MaterialItem("MAS-006","Masilla RF","Resistente al fuego","Rojo","img_compound_fire",
                getMeasuresByMaterialId("MAS-006")),
        )
    )

    private val tapesGroup = MaterialGroup(
        id = "GRP-CIN",
        name = "Cintas",
        description = "Cintas de papel, fibra de vidrio, malla autoadhesiva y sellado",
        imageRef = "img_group_tapes",
        category = MaterialCategory.TAPES,
        materials = listOf(
            MaterialItem("CIN-001","Cinta Papel Perforado","Refuerzo de juntas con masilla","Kraft","img_tape_paper_perforated",
                getMeasuresByMaterialId("CIN-001")),
            MaterialItem("CIN-003","Cinta Fibra de Vidrio","Refuerzo de juntas resistente","Blanco","img_tape_fiberglass",
                getMeasuresByMaterialId("CIN-003")),
            MaterialItem("CIN-004","Cinta Malla Autoadhesiva","F\u00e1cil aplicaci\u00f3n sin masilla previa","Blanco","img_tape_mesh",
                getMeasuresByMaterialId("CIN-004")),
        )
    )

    private val beadsGroup = MaterialGroup(
        id = "GRP-ESQ",
        name = "Esquineros y Protecciones",
        description = "Perfiles esquinero, guardacantos y protecciones de borde",
        imageRef = "img_group_beads",
        category = MaterialCategory.BEADS,
        materials = listOf(
            MaterialItem("ESQ-001","Esquinero Met\u00e1lico","Protecci\u00f3n de esquinas","Galvanizado","img_bead_metal_perforated",
                getMeasuresByMaterialId("ESQ-001")),
            MaterialItem("ESQ-004","Esquinero PVC","Flexible para esquinas","Blanco","img_bead_pvc",
                getMeasuresByMaterialId("ESQ-004")),
            MaterialItem("ESQ-009","Guardacantos Aluminio","Protecci\u00f3n de bordes","Aluminio","img_bead_edge",
                getMeasuresByMaterialId("ESQ-009")),
        )
    )

    private val ceilingsGroup = MaterialGroup(
        id = "GRP-TCH",
        name = "Techos Registrables",
        description = "Sistemas de techos suspendidos, perfiles T y baldosas de cielo raso",
        imageRef = "img_group_ceilings",
        category = MaterialCategory.CEILINGS,
        materials = listOf(
            MaterialItem("RET-001","Perfiles T Principales","Perfil principal techo reticulado","Blanco","img_grid_main_t24",
                getMeasuresByMaterialId("RET-001")),
            MaterialItem("RET-002","Perfiles T Secundarios","Perfil de cruce y tabica","Blanco","img_grid_cross_t24",
                getMeasuresByMaterialId("RET-002")),
            MaterialItem("RET-003","Angulares Perimetrales","Per\u00edmetro del falso techo","Blanco","img_grid_angle_l24",
                getMeasuresByMaterialId("RET-003")),
            MaterialItem("BAL-001","Baldosas Minerales","Cielo raso absorbente ac\u00fastico","Blanco","img_tile_mineral_600",
                getMeasuresByMaterialId("BAL-001")),
            MaterialItem("BAL-005","Baldosas Met\u00e1licas","Cielo raso microperforado","Plateado","img_tile_metal_600",
                getMeasuresByMaterialId("BAL-005")),
        )
    )

    private val accessoriesGroup = MaterialGroup(
        id = "GRP-ACC",
        name = "Complementos",
        description = "Anclajes, alambres, adhesivos y accesorios diversos",
        imageRef = "img_group_accessories",
        category = MaterialCategory.ACCESSORIES,
        materials = listOf(
            MaterialItem("ACC-001","Alambre Galvanizado","Suspensi\u00f3n de techos","Galvanizado","img_ceiling_wire_12",
                getMeasuresByMaterialId("ACC-001")),
            MaterialItem("BAR-001","Film Polietileno","Barrera de vapor","Transparente","img_vapor_barrier_pe",
                getMeasuresByMaterialId("BAR-001")),
            MaterialItem("BAR-003","Membrana Tyvek","Barrera transpirable exterior","Blanco","img_weather_barrier",
                getMeasuresByMaterialId("BAR-003")),
        )
    )

    // ──────────────────────────────────────────────
    // HELPERS
    // ──────────────────────────────────────────────
    private fun v(dimension: String, value: Double, unit: String = "mm"): MeasureValue =
        MeasureValue(dimension, value, unitEnum(unit))

    private fun v(dimension: String, value: String): MeasureValue =
        MeasureValue(dimension, 0.0, MeasurementUnit.UNKNOWN)

    private fun unitEnum(s: String): MeasurementUnit = when (s) {
        "mm" -> MeasurementUnit.MM
        "m" -> MeasurementUnit.M
        "kg" -> MeasurementUnit.KG
        "L" -> MeasurementUnit.L
        else -> MeasurementUnit.MM
    }

    private fun mm(id: String, name: String, matId: String, code: String,
                   vals: List<MeasureValue>, tg: List<String>, img: String,
                   std: StandardRegion = StandardRegion.EU,
                   desc: String = ""): MaterialMeasure {
        val d = if (desc.isEmpty()) {
            if (vals.any { it.dimension == "Peso" }) {
                "Medida $code. Peso: ${vals.first { it.dimension == "Peso" }.formatted()}."
            } else ""
        } else desc
        return MaterialMeasure(id, matId, name, categoryFromId(matId), vals, tg, std, img, description = d)
    }

    private fun mmm(id: String, name: String, matId: String, code: String,
                    vals: List<MeasureValue>, tg: List<String>, img: String,
                    std: StandardRegion = StandardRegion.EU): MaterialMeasure =
        MaterialMeasure(id, matId, name, categoryFromId(matId), vals, tg, std, img, consumptionRate = null)

    private fun categoryFromId(materialId: String): MaterialCategory = when {
        materialId.startsWith("PLY") -> MaterialCategory.PLASTERBOARD
        materialId.startsWith("PER") || materialId.startsWith("CAN") ||
            materialId.startsWith("MON") || materialId.startsWith("OMG") ||
            materialId.startsWith("STEEL") -> MaterialCategory.PROFILES
        materialId.startsWith("TOR") || materialId.startsWith("TRN") ||
            materialId.startsWith("CLV") || materialId.startsWith("ANC") -> MaterialCategory.FASTENERS
        materialId.startsWith("AIS") -> MaterialCategory.INSULATION
        materialId.startsWith("MAS") -> MaterialCategory.COMPOUNDS
        materialId.startsWith("CIN") -> MaterialCategory.TAPES
        materialId.startsWith("ESQ") || materialId.startsWith("GC") -> MaterialCategory.BEADS
        materialId.startsWith("RET") || materialId.startsWith("BAL") ||
            materialId.startsWith("TILE") -> MaterialCategory.CEILINGS
        materialId.startsWith("ACC") || materialId.startsWith("BAR") ||
            materialId.startsWith("WIRE") -> MaterialCategory.ACCESSORIES
        else -> MaterialCategory.ACCESSORIES
    }

    private fun tags(vararg t: String): List<String> = t.toList()
}
