package code.editor.mon

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SharedViewModel : ViewModel() {
    val openFileRequest = MutableLiveData<FileOpenRequest?>()
    val projectLoaded = MutableLiveData<Boolean>()
}
