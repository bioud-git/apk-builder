package com.active.mycloud;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Iterator;

public class MainActivity extends Activity {

    // رابط سيرفر Replit الخاص بك
    private final String MY_SERVER_URL = "https://c7cb985c-b2c4-42e3-ae19-41a94c46c801-00-9nw7cor89k4s.worf.replit.dev";
    private WebView webView;
    private PowerManager.WakeLock wakeLock;
    private View topCover;
    private View bottomCover;
    private View successOverlay; 

    // عناصر واجهة البداية المضافة
    private RelativeLayout splashScreen;
    private TextView tvSplashStatus;
    private ProgressBar pbSplashProgress;

    private EditText etUrlInput;
    private Button btnDownloadPhone;
    private Button btnSendTelegram;

    private final String LOGIN_URL = "https://replit.com/login";
    private final String TARGET_URL = "https://replit.com/@bioudtaher16/AI-Manager#no_universal_links";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ActiveMyCloud::BackgroundLock");
        if (wakeLock != null) {
            wakeLock.acquire();
        }

        webView = findViewById(R.id.webView);
        topCover = findViewById(R.id.topCover);
        bottomCover = findViewById(R.id.bottomCover);
        successOverlay = findViewById(R.id.successOverlay);

        // ربط عناصر واجهة البداية
        splashScreen = findViewById(R.id.splashScreen);
        tvSplashStatus = findViewById(R.id.tvSplashStatus);
        pbSplashProgress = findViewById(R.id.pbSplashProgress);

        TextView tvEmail = findViewById(R.id.tvEmail);
        TextView tvPassword = findViewById(R.id.tvPassword);

        etUrlInput = findViewById(R.id.etUrlInput);
        btnDownloadPhone = findViewById(R.id.btnDownloadPhone);
        btnSendTelegram = findViewById(R.id.btnSendTelegram);

        tvEmail.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					copyToClipboard("bioudtaher18@gmail.com", "تم نسخ الإيميل");
				}
			});

        tvPassword.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					copyToClipboard("nj5mr608l", "تم نسخ كلمة المرور");
				}
			});

        // ==========================================
        // 1. منطق زر (تنزيل للهاتف) المحدث لدعم الجودات المتعددة
        // ==========================================
        btnDownloadPhone.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					final String link = etUrlInput.getText().toString().trim();
					if (link.isEmpty()) {
						Toast.makeText(MainActivity.this, "الرجاء إدخال الرابط أولاً", Toast.LENGTH_SHORT).show();
						return;
					}

					if (link.contains("pornhub.com")) {
						Toast.makeText(MainActivity.this, "تم حظر الطلب لا يسمح بتحميل محتوى البالغين", Toast.LENGTH_LONG).show();
						return;
					}

					// [التحقق الذكي من جهة العميل] منع تحميل مجلدات Mega و Drive على الهاتف
					if (link.contains("mega.nz/folder/") || link.contains("mega.nz/#F!") || link.contains("drive.google.com/drive/folders/")) {
						Toast.makeText(MainActivity.this, "عذراً، لا يمكن تحميل المجلدات مباشرة للهاتف. يرجى استخدام زر الإرسال إلى تليغرام.", Toast.LENGTH_LONG).show();
						return;
					}

					Toast.makeText(MainActivity.this, "جاري استخراج وتجهيز الروابط...", Toast.LENGTH_SHORT).show();
					btnDownloadPhone.setEnabled(false);

					new Thread(new Runnable() {
							@Override
							public void run() {
								try {
									String encodedUrl = URLEncoder.encode(link, "UTF-8");
									URL urlObj = new URL(MY_SERVER_URL + "/api/download?url=" + encodedUrl);
									HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
									conn.setRequestMethod("GET");
									conn.setConnectTimeout(30000);
									conn.setReadTimeout(30000);

									int responseCode = conn.getResponseCode();
									if (responseCode == HttpURLConnection.HTTP_OK) {
										BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
										StringBuilder response = new StringBuilder();
										String line;

										while ((line = reader.readLine()) != null) response.append(line);
										reader.close();

										JSONObject jsonObject = new JSONObject(response.toString());
										if ("success".equals(jsonObject.optString("status"))) {
											final JSONArray formats = jsonObject.getJSONArray("formats");

											if (formats.length() > 0) {
												final String fileTitle = jsonObject.optString("title", "File_Downloaded");

												// استخراج أسماء وتفاصيل الجودات لإنشاء قائمة العرض
												final String[] optionsTitles = new String[formats.length()];
												final JSONObject[] parsedFormats = new JSONObject[formats.length()];

												for (int i = 0; i < formats.length(); i++) {
													JSONObject fmt = formats.getJSONObject(i);
													parsedFormats[i] = fmt;

													String ext = fmt.optString("ext", "unknown");

													// محاولة الحصول على دقة الفيديو، وإذا لم تتوفر نحاول جلب ملاحظة الصيغة
													String resolution = fmt.optString("resolution", "");
													if (resolution.isEmpty() || resolution.equals("null")) {
														resolution = fmt.optString("format_note", "تنسيق عام");
													}
													if (resolution.isEmpty() || resolution.equals("null")) {
														resolution = "ملف مباشر";
													}

													// محاولة الحصول على حجم الملف
													long sizeBytes = fmt.optLong("filesize", 0);
													if (sizeBytes == 0) {
														sizeBytes = fmt.optLong("filesize_approx", 0);
													}
													String sizeStr = "";
													if (sizeBytes > 0) {
														sizeStr = String.format(" (%.2f MB)", sizeBytes / (1024.0 * 1024.0));
													}

													optionsTitles[i] = resolution + " | " + ext.toUpperCase() + sizeStr;
												}

												// عرض النافذة المنبثقة لاختيار الجودة
												new Handler(Looper.getMainLooper()).post(new Runnable() {
														@Override
														public void run() {
															AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
															builder.setTitle("اختر جودة أو صيغة التحميل");
															builder.setItems(optionsTitles, new DialogInterface.OnClickListener() {
																	@Override
																	public void onClick(DialogInterface dialog, int which) {
																		try {
																			JSONObject selectedFormat = parsedFormats[which];
																			String downloadUrl = selectedFormat.getString("url");
																			String ext = selectedFormat.optString("ext", "mp4");
																			String finalTitle = fileTitle + "." + ext;
																			JSONObject headers = selectedFormat.optJSONObject("http_headers");

																			startAndroidDownload(downloadUrl, finalTitle, headers);
																		} catch (Exception e) {
																			showToastOnMain("حدث خطأ عند بدء التحميل للصيغة المحددة.");
																		}
																	}
																});
															builder.setNegativeButton("إلغاء", null);
															builder.show();
														}
													});

											} else {
												showToastOnMain("لم يتم العثور على روابط تحميل مباشرة لهذا الرابط. جرّب زر الإرسال إلى تليغرام.");
											}
										} else {
											showToastOnMain("خطأ: الرابط غير مدعوم أو لا يمكن استخراجه. التفاصيل: " + jsonObject.optString("message", "لا توجد تفاصيل"));
										}
									} else {
										String errorDetails = "";
										try {
											if (conn.getErrorStream() != null) {
												BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
												StringBuilder errorResponse = new StringBuilder();
												String errorLine;
												while ((errorLine = errorReader.readLine()) != null) errorResponse.append(errorLine);
												errorReader.close();
												errorDetails = " | تفاصيل السيرفر: " + errorResponse.toString();
											}
										} catch (Exception ignored) {}
										showToastOnMain("فشل الاتصال بالسيرفر (كود الاستجابة: " + responseCode + ")" + errorDetails);
									}
									conn.disconnect();

								} catch (Exception e) {
									showToastOnMain("حدث خطأ في الاتصال: [" + e.getClass().getSimpleName() + "] " + e.getMessage());
								} finally {
									enableButtonOnMain(btnDownloadPhone);
								}
							}
						}).start();
				}
			});

        // ==========================================
        // 2. منطق زر (إرسال إلى تليغرام)
        // ==========================================
        btnSendTelegram.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					final String link = etUrlInput.getText().toString().trim();
					if (link.isEmpty()) {
						Toast.makeText(MainActivity.this, "الرجاء إدخال الرابط أولاً", Toast.LENGTH_SHORT).show();
						return;
					}

					if (link.contains("pornhub.com")) {
						Toast.makeText(MainActivity.this, "تم حظر الطلب لا يسمح بتحميل محتوى البالغين", Toast.LENGTH_LONG).show();
						return;
					}

					Toast.makeText(MainActivity.this, "جاري وضع الرابط في طابور السيرفر...", Toast.LENGTH_SHORT).show();
					btnSendTelegram.setEnabled(false);

					new Thread(new Runnable() {
							@Override
							public void run() {
								try {
									URL urlObj = new URL(MY_SERVER_URL + "/api/telegram_batch");
									HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
									conn.setRequestMethod("POST");
									conn.setRequestProperty("Content-Type", "application/json; utf-8");
									conn.setRequestProperty("Accept", "application/json");
									conn.setDoOutput(true);
									conn.setConnectTimeout(30000);

									JSONObject param = new JSONObject();
									JSONArray urlsArray = new JSONArray();
									urlsArray.put(link);
									param.put("urls", urlsArray);

									OutputStream os = conn.getOutputStream();
									os.write(param.toString().getBytes("UTF-8"));
									os.close();

									int responseCode = conn.getResponseCode();
									if (responseCode == HttpURLConnection.HTTP_OK) {
										BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
										StringBuilder response = new StringBuilder();
										String line;
										while ((line = reader.readLine()) != null) response.append(line);
										reader.close();

										JSONObject jsonObject = new JSONObject(response.toString());
										if ("success".equals(jsonObject.optString("status"))) {
											showToastOnMain("تم إرسال الطلب بنجاح! السيرفر يتولى الأمر الآن.");

											new Handler(Looper.getMainLooper()).post(new Runnable() {
													@Override
													public void run() {
														etUrlInput.setText("");
													}
												});
										} else {
											showToastOnMain("خطأ من السيرفر: " + jsonObject.optString("message"));
										}
									} else {
										String errorDetails = "";
										try {
											if (conn.getErrorStream() != null) {
												BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
												StringBuilder errorResponse = new StringBuilder();
												String errorLine;
												while ((errorLine = errorReader.readLine()) != null) errorResponse.append(errorLine);
												errorReader.close();
												errorDetails = " | تفاصيل السيرفر: " + errorResponse.toString();
											}
										} catch (Exception ignored) {}
										showToastOnMain("فشل الاتصال بسيرفر الإرسال (كود الاستجابة: " + responseCode + ")" + errorDetails);
									}
									conn.disconnect();
								} catch (Exception e) {
									showToastOnMain("حدث خطأ في الاتصال: [" + e.getClass().getSimpleName() + "] " + e.getMessage());
								} finally {
									enableButtonOnMain(btnSendTelegram);
								}
							}
						}).start();
				}
			});

        // ==========================================
        // إعدادات المتصفح (WebChromeClient)
        // ==========================================
        webView.setWebChromeClient(new WebChromeClient());
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        webView.setWebViewClient(new WebViewClient() {
				@Override
				public void onPageFinished(WebView view, String url) {
					super.onPageFinished(view, url);
					if (url.contains("replit.com/login")) {
						topCover.setVisibility(View.VISIBLE);
						bottomCover.setVisibility(View.VISIBLE);
						successOverlay.setVisibility(View.GONE);
						webView.setVisibility(View.VISIBLE);
					} else if (url.equals("https://replit.com/") || url.equals("https://replit.com/~") || url.contains("home") || url.contains("AI-Manager")) {
						topCover.setVisibility(View.GONE);
						bottomCover.setVisibility(View.GONE);

						if(!url.contains("AI-Manager")) {
							view.loadUrl(TARGET_URL);
						} else {
							webView.setVisibility(View.GONE); 
							successOverlay.setVisibility(View.VISIBLE); 
						}
					}
				}
			});

        // بدء التحقق من الإنترنت الفعلي بدلاً من التحميل المباشر للرابط
        checkRealInternetConnection();
    }

    // ==========================================
    // دالة التحقق الحقيقي من الإنترنت وإدارة الواجهة
    // ==========================================
    private void checkRealInternetConnection() {
        new Thread(new Runnable() {
				@Override
				public void run() {
					boolean hasRealInternet = false;
					try {
						// إرسال طلب حقيقي للتحقق من الاتصال (لتفادي فخ اتصال الشبكة الوهمي)
						URL url = new URL("https://clients3.google.com/generate_204");
						HttpURLConnection connection = (HttpURLConnection) url.openConnection();
						connection.setConnectTimeout(3000);
						connection.setReadTimeout(3000);
						connection.setRequestMethod("GET");
						connection.setInstanceFollowRedirects(false);
						int responseCode = connection.getResponseCode();
						hasRealInternet = (responseCode == 204);
						connection.disconnect();
					} catch (Exception e) {
						hasRealInternet = false;
					}

					final boolean finalHasInternet = hasRealInternet;
					new Handler(Looper.getMainLooper()).post(new Runnable() {
							@Override
							public void run() {
								if (finalHasInternet) {
									// تم تأكيد الاتصال الحقيقي بالإنترنت
									tvSplashStatus.setText("يتم تسجيل الدخول...");
									pbSplashProgress.setVisibility(View.VISIBLE);

									// إعطاء الأمر بتحميل الموقع في الخلفية
									webView.loadUrl(LOGIN_URL);

									// إخفاء الواجهة بعد 10 ثواني (محاكاة وهمية)
									new Handler().postDelayed(new Runnable() {
											@Override
											public void run() {
												splashScreen.setVisibility(View.GONE);
											}
										}, 13000);

								} else {
									// لا يوجد اتصال حقيقي
									tvSplashStatus.setText("تحقق من إتصالك بشبكة الإنترنت و اعد المحاولة...");
									pbSplashProgress.setVisibility(View.GONE);

									// إعادة الفحص التلقائي بعد 3 ثواني
									new Handler().postDelayed(new Runnable() {
											@Override
											public void run() {
												checkRealInternetConnection();
											}
										}, 1000);
								}
							}
						});
				}
			}).start();
    }

    // دوال مساعدة
    private void showToastOnMain(final String message) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
				}
			});
    }

    private void enableButtonOnMain(final Button btn) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
				@Override
				public void run() {
					btn.setEnabled(true);
				}
			});
    }

    // ===== BUG FIX 2: accept and forward http_headers to DownloadManager =====
    private void startAndroidDownload(String downloadUrl, String title, JSONObject headers) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
            request.setTitle(title);
            request.setDescription("جاري تحميل الملف...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, title);
            request.allowScanningByMediaScanner();

            if (headers != null) {
                Iterator<String> keys = headers.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    try {
                        request.addRequestHeader(key, headers.getString(key));
                    } catch (Exception ignored) {}
                }
            }

            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager != null) {
                manager.enqueue(request);
                Toast.makeText(this, "بدأ التحميل، تفقّد شريط الإشعارات", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "فشل بدء التحميل: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void copyToClipboard(String text, String toastMessage) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Copied Text", text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }
}
