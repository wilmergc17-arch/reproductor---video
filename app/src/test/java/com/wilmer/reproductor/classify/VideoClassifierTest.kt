package com.wilmer.reproductor.classify

import com.wilmer.reproductor.classify.VideoKind.Episode
import com.wilmer.reproductor.classify.VideoKind.Movie
import com.wilmer.reproductor.classify.VideoKind.Other
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoClassifierTest {

    private fun classify(name: String, folder: String = "", durationMs: Long = 0) =
        VideoClassifier.classify(name, folder, durationMs)

    @Test
    fun seriesConPatronSxE() {
        assertEquals(
            Episode("Breaking Bad", 1, 2, "Cat's in the Bag"),
            classify("Breaking.Bad.S01E02.Cat's.in.the.Bag.720p.BluRay.x264.mkv"),
        )
        assertEquals(Episode("The Office", 3, 12, null), classify("the_office_s03e12.mp4"))
        assertEquals(Episode("Dark", 2, 1, null), classify("Dark S02 E01 1080p WEB-DL.mkv"))
    }

    @Test
    fun seriesConPatron1x02() {
        assertEquals(Episode("Friends", 1, 2, null), classify("Friends 1x02.avi"))
    }

    @Test
    fun seriesEnEspanol() {
        assertEquals(
            Episode("La Casa de Papel", 2, 5, null),
            classify("La Casa de Papel Temporada 2 Capitulo 5.mp4"),
        )
        assertEquals(Episode("El Chavo", 1, 5, null), classify("El Chavo Cap 105.mp4"))
        assertEquals(
            Episode("Pasion de Gavilanes", 1, 23, null),
            classify("Pasion de Gavilanes - Capítulo 23.mp4"),
        )
    }

    @Test
    fun serieTomadaDeLaCarpeta() {
        assertEquals(
            Episode("Stranger Things", 1, 3, null),
            classify("Episodio 3.mp4", "Series/Stranger Things/Temporada 1/"),
        )
        assertEquals(
            Episode("Lost", 2, 4, null),
            classify("04.mkv", "Download/Lost/Season 2"),
        )
        assertEquals(Episode("Narcos", 1, 1, null), classify("S01E01.mkv", "Movies/Narcos/"))
    }

    @Test
    fun anime() {
        assertEquals(
            Episode("Shingeki no Kyojin", 1, 5, null),
            classify("[SubsPlease] Shingeki no Kyojin - 05 (1080p) [ABCD1234].mkv"),
        )
    }

    @Test
    fun peliculasPorAnio() {
        assertEquals(Movie("Inception", 2010), classify("Inception (2010) 1080p.mkv"))
        assertEquals(Movie("The Matrix", 1999), classify("The.Matrix.1999.BluRay.x264.mp4"))
        assertEquals(Movie("2012", 2009), classify("2012 (2009).mp4"))
        assertEquals(Movie("El Laberinto Del Fauno", 2006), classify("el laberinto del fauno 2006.avi"))
    }

    @Test
    fun peliculasPorEtiquetasCarpetaODuracion() {
        assertEquals(Movie("Avatar", null), classify("Avatar.BRRip.XviD.avi"))
        assertEquals(Movie("Coco", null), classify("Coco.mp4", "Movies/"))
        assertEquals(Movie("Mi Video Largo", null), classify("mi video largo.mp4", "", 95 * 60_000L))
    }

    @Test
    fun videosPersonalesSonOtros() {
        assertEquals(Other::class, classify("VID_20240512_123456.mp4", "DCIM/Camera/")::class)
        assertEquals(Other::class, classify("VID-20240512-WA0003.mp4", "WhatsApp/Media/WhatsApp Video/")::class)
        assertEquals(
            Other::class,
            classify("Screen_Recording_20240102-101010.mp4", "", 2 * 60 * 60_000L)::class,
        )
        assertEquals(Other("Cumpleaños De Ana"), classify("cumpleaños de ana.mp4", "", 5 * 60_000L))
    }

    @Test
    fun claveDeSerieAgrupaVariantes() {
        assertEquals(VideoClassifier.showKey("Breaking Bad"), VideoClassifier.showKey("breaking.bad"))
    }
}
