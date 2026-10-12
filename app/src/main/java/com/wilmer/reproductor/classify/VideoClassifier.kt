package com.wilmer.reproductor.classify

/** Tipo de video deducido de su duración, el nombre del archivo y su carpeta. */
sealed interface VideoKind {
    val title: String

    data class Movie(override val title: String, val year: Int?) : VideoKind

    data class Episode(
        val show: String,
        val season: Int,
        val episode: Int,
        val episodeTitle: String?,
    ) : VideoKind {
        override val title: String get() = episodeTitle ?: "Episodio $episode"
    }

    /** Video corto (menos de 10 minutos). */
    data class Reel(override val title: String) : VideoKind

    /** Sin duración conocida ni pistas en el nombre. */
    data class Other(override val title: String) : VideoKind
}

/**
 * Clasificador local (sin internet).
 *
 * La duración decide el tipo: una hora o más es película, de 10 a 59 minutos es serie y menos
 * de 10 minutos es reel. El nombre del archivo sirve para agrupar los episodios por serie y
 * temporada ("S01E02", "1x02", "Temporada 1 Capítulo 2"…) y para limpiar los títulos
 * ("Coco (2017) 1080p" → "Coco", 2017). Sin duración conocida se decide solo por el nombre.
 */
object VideoClassifier {

    const val MOVIE_MIN_DURATION_MS = 60 * 60 * 1000L
    const val REEL_MAX_DURATION_MS = 10 * 60 * 1000L

    private val EXTENSION = Regex("""\.[A-Za-z0-9]{2,4}$""")
    // Llaves y corchetes escapados: el motor de expresiones de Android (ICU) no acepta "}" suelto
    private val BRACKETS = Regex("""\[[^\]]*\]|\{[^\}]*\}""")
    private val EMPTY_PARENS = Regex("""\(\s*\)""")
    private val SPACES = Regex("""\s+""")
    private val WEBSITE = Regex("""(?i)^www \S+ (com|net|org|to|tv|mx|co|es)\b[\s-]*""")

    // Etiquetas técnicas: el título termina donde empieza la primera
    private val TAG = Regex(
        """(?i)\b(2160p|1080p|1080i|720p|576p|480p|360p|4k|uhd|hdr10?|bluray|blu-ray|bdrip|brrip|bdremux|remux|web-?dl|webrip|hdtv|hdrip|dvdrip|dvdscr|camrip|x264|x265|h ?264|h ?265|hevc|xvid|divx|aac|ac3|dts|latino|castellano|espa[nñ]ol|spanish|english|dual|multi|subtitulado|vose|proper|repack|extended|unrated|imax|10bit)\b"""
    )

    // Etiquetas de origen que casi siempre indican una película descargada
    private val MOVIE_TAG = Regex(
        """(?i)\b(bluray|blu-ray|bdrip|brrip|bdremux|remux|web-?dl|webrip|hdrip|dvdrip|dvdscr|camrip|xvid|divx|x264|x265)\b"""
    )

    private val SXE = Regex("""(?i)\bs(\d{1,2})[ -]?e(\d{1,3})(?:-?e\d{1,3})*(?!\d)""")
    private val NXN = Regex("""(?i)\b(\d{1,2})x(\d{2,3})\b""")
    private val SEASON_WORD = Regex("""(?i)\b(?:temporada|season|temp|t)\s?(\d{1,2})\b""")
    private val EPISODE_WORD =
        Regex("""(?i)\b(cap[ií]tulo|cap|episodio|episode|ep|e)\s?(\d{1,4})\b""")
    private val ANIME_DASH = Regex("""^(.+?)\s-\s(\d{1,3})(?:v\d)?(?=\s|$)""")
    private val LEADING_NUMBER = Regex("""^(\d{1,3})(?=\s|$|-)""")
    private val SEASON_FOLDER = Regex("""(?i)^(?:temporada|season|temp|s)\s?(\d{1,2})$""")

    private val YEAR = Regex("""(?<![0-9])(19[3-9]\d|20[0-4]\d)(?![0-9])""")
    private val TIMESTAMP =
        Regex("""\d{8}[ _-]?\d{6}|\d{4}[-_.]\d{2}[-_.]\d{2}(?:[ _-]+(?:at[ _-]+)?\d{2}[-_.]?\d{2}(?:[-_.]?\d{2})?)?""")
    private val CAMERA_NAME = Regex(
        """(?i)^(vid|img|pxl|mov|dsc|dji|gopr|gh\d{2}|mvimg|trim|screenrecorder|screen[ _-]?record(ing)?|screencast|grabaci[oó]n|record|video|whatsapp video)[ _-]?\d"""
    )
    private val CAMERA_FOLDER = Regex(
        """(?i)(^|/)(dcim|camera|c[aá]mara|screenshots|screenrecorder|screen recordings|whatsapp video|whatsapp animated gifs)(/|$)"""
    )
    private val MOVIE_FOLDER = Regex("""(?i)(^|/)(pel[ií]culas|peliculas|movies|films|cine)(/|$)""")
    private val GENERIC_FOLDERS = setOf(
        "download", "downloads", "descargas", "movies", "películas", "peliculas", "films",
        "video", "videos", "series", "tv", "tv shows", "anime", "telegram", "telegram video",
        "media", "storage", "emulated", "0", "sdcard", "dcim", "camera",
    )

    fun classify(fileName: String, folderPath: String = "", durationMs: Long = 0): VideoKind {
        val byName = classifyByName(fileName, folderPath)
        if (durationMs <= 0) return byName
        val plainTitle = fileName.replace(EXTENSION, "").let { cleanTitle(normalize(it)).ifEmpty { it } }
        return when {
            durationMs >= MOVIE_MIN_DURATION_MS ->
                byName as? VideoKind.Movie
                    ?: VideoKind.Movie(if (byName is VideoKind.Other) byName.title else plainTitle, null)
            durationMs < REEL_MAX_DURATION_MS ->
                VideoKind.Reel(if (byName is VideoKind.Episode) plainTitle else byName.title)
            else -> byName as? VideoKind.Episode ?: run {
                // Serie sin número de episodio en el nombre: se agrupa por su carpeta
                val folders = splitFolders(folderPath)
                val season = folders.lastOrNull()?.let { SEASON_FOLDER.matchEntire(it) }
                    ?.groupValues?.get(1)?.toInt() ?: 1
                VideoKind.Episode(showFromFolders(folders, useGenericFolder = true), season, 0, byName.title)
            }
        }
    }

    /** Clasificación solo por el nombre, para cuando no se conoce la duración. */
    private fun classifyByName(fileName: String, folderPath: String): VideoKind {
        val base = fileName.replace(EXTENSION, "")
        val normalized = normalize(base)
        val folders = splitFolders(folderPath)

        episodeOf(normalized, folders)?.let { return it }

        val noDates = normalized.replace(TIMESTAMP, " ")
        val isPersonal = CAMERA_NAME.containsMatchIn(base) || CAMERA_FOLDER.containsMatchIn(folderPath)
        if (isPersonal) return VideoKind.Other(cleanTitle(noDates).ifEmpty { base })

        // Año: se usa el último que tenga un título delante, como en "2012 (2009)"
        val year = YEAR.findAll(noDates).lastOrNull { cleanTitle(noDates.substring(0, it.range.first)).isNotEmpty() }
        if (year != null) {
            val title = cleanTitle(noDates.substring(0, year.range.first))
            return VideoKind.Movie(title, year.value.toInt())
        }

        val title = cleanTitle(noDates).ifEmpty { base }
        if (MOVIE_TAG.containsMatchIn(noDates) || MOVIE_FOLDER.containsMatchIn(folderPath)) {
            return VideoKind.Movie(title, null)
        }
        return VideoKind.Other(title)
    }

    /** Clave para agrupar episodios de la misma serie aunque el nombre varíe un poco. */
    fun showKey(show: String): String =
        show.lowercase().filter { it.isLetterOrDigit() }

    private fun episodeOf(name: String, folders: List<String>): VideoKind.Episode? {
        val folderSeason = folders.lastOrNull()?.let { SEASON_FOLDER.matchEntire(it) }
            ?.groupValues?.get(1)?.toInt()

        val direct = SXE.find(name) ?: NXN.find(name)
        if (direct != null) {
            return episode(
                name, folders, direct.range,
                season = direct.groupValues[1].toInt(),
                number = direct.groupValues[2].toInt(),
            )
        }

        val epWord = EPISODE_WORD.find(name)
        if (epWord != null) {
            val word = epWord.groupValues[1].lowercase()
            val digits = epWord.groupValues[2]
            val seasonWord = SEASON_WORD.findAll(name).firstOrNull { it.range.last < epWord.range.first }
            var season = seasonWord?.groupValues?.get(1)?.toInt() ?: folderSeason
            var number = digits.toInt()
            // "Capítulo 105" suele significar temporada 1, episodio 05
            if (season == null && word.startsWith("cap") && digits.length >= 3) {
                season = number / 100
                number %= 100
            }
            // Un "E" suelto solo cuenta si hay temporada o va con dos cifras ("E05")
            if (word == "e" && season == null && digits.length < 2) return null
            val start = seasonWord?.range?.first ?: epWord.range.first
            return episode(name, folders, start..epWord.range.last, season ?: 1, number)
        }

        val noBrackets = name.replace(BRACKETS, " ").replace(SPACES, " ").trim()
        ANIME_DASH.find(noBrackets)?.let { m ->
            val show = cleanTitle(m.groupValues[1])
            if (show.isNotEmpty() && !YEAR.matches(m.groupValues[2])) {
                return episode(noBrackets, folders, m.groups[2]!!.range, folderSeason ?: 1, m.groupValues[2].toInt())
            }
        }

        if (folderSeason != null) {
            LEADING_NUMBER.find(noBrackets)?.let { m ->
                return episode(noBrackets, folders, m.range, folderSeason, m.groupValues[1].toInt())
            }
        }
        return null
    }

    private fun episode(
        name: String,
        folders: List<String>,
        match: IntRange,
        season: Int,
        number: Int,
    ): VideoKind.Episode {
        val show = cleanTitle(name.substring(0, match.first)).ifEmpty { showFromFolders(folders) }
        val rest = cleanTitle(name.substring(match.last + 1))
        return VideoKind.Episode(show, season, number, rest.ifEmpty { null })
    }

    private fun splitFolders(folderPath: String) =
        folderPath.split('/', '\\').map { it.trim() }.filter { it.isNotEmpty() }

    private fun showFromFolders(folders: List<String>, useGenericFolder: Boolean = false): String {
        val candidates = folders.asReversed().filter { SEASON_FOLDER.matchEntire(it) == null }
        val folder = candidates.firstOrNull { it.lowercase() !in GENERIC_FOLDERS }
            ?: candidates.firstOrNull()?.takeIf { useGenericFolder }
        return folder?.let { cleanTitle(normalize(it)) }?.ifEmpty { null }
            ?: if (useGenericFolder) "Sin carpeta" else "Sin nombre"
    }

    private fun normalize(text: String): String =
        text.replace('.', ' ').replace('_', ' ').replace(SPACES, " ").trim()

    private fun cleanTitle(text: String): String {
        var t = text.replace(BRACKETS, " ").replace(WEBSITE, "")
        TAG.find(t)?.let { t = t.substring(0, it.range.first) }
        t = t.replace(EMPTY_PARENS, " ").replace(SPACES, " ")
            .trim { it.isWhitespace() || it in "-–—_.,:;(+" }
        if (t.isNotEmpty() && t == t.lowercase()) {
            t = t.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
        }
        return t
    }
}
