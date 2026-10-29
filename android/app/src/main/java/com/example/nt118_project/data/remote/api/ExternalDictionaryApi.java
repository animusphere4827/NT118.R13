package com.example.nt118_project.data.remote.api;

import com.example.nt118_project.data.remote.dto.DatamuseWordDto;
import com.example.nt118_project.data.remote.dto.FreeDictionaryEntryDto;
import com.example.nt118_project.data.remote.dto.TranslationDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Từ điển tiếng Anh mở bên ngoài, dùng cho từ không có trong DB của nhóm. Không cần key, không gửi token.
 * Mỗi dịch vụ một host nên khai báo URL tuyệt đối.
 */
public interface ExternalDictionaryApi {

    /** Datamuse: gợi ý từ tiếng Anh theo tiền tố, sắp theo độ phổ biến. */
    @GET("https://api.datamuse.com/sug")
    Call<List<DatamuseWordDto>> suggest(@Query("s") String prefix, @Query("max") int max);

    /** Free Dictionary API: IPA, file phát âm, loại từ, định nghĩa, ví dụ. 404 khi không có từ. */
    @GET("https://api.dictionaryapi.dev/api/v2/entries/en/{word}")
    Call<List<FreeDictionaryEntryDto>> entries(@Path("word") String word);

    /** MyMemory: dịch máy, langpair "en|vi". Hạn mức miễn phí khoảng 5.000 ký tự/ngày. */
    @GET("https://api.mymemory.translated.net/get")
    Call<TranslationDto> translate(@Query("q") String text, @Query("langpair") String langPair);
}
