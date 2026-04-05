package fulguris.js

import com.anthonycr.mezzanine.FileStream

/**
 * Hooks URL.createObjectURL to store Blob references so they remain
 * accessible even after the page revokes the object URL.
 */
@FileStream("src/main/js/BlobHook.js")
interface BlobHook {
    fun provideJs(): String
}
