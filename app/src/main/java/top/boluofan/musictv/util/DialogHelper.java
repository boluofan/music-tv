package top.boluofan.musictv.util;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import java.util.Hashtable;
import top.boluofan.musictv.R;

public class DialogHelper {

    public interface IDialogCallback {
        void onConfirm();
        void onCancel();
    }

    public static AlertDialog showConfirmDialog(Context context, String title, String message, String confirmText, String cancelText, IDialogCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage(message);

        builder.setPositiveButton(confirmText, (dialog, which) -> {
            if (callback != null) callback.onConfirm();
        });

        builder.setNegativeButton(cancelText, (dialog, which) -> {
            if (callback != null) callback.onCancel();
        });

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(d -> {
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);

            if (positiveButton != null) {
                positiveButton.setTextSize(16);
                positiveButton.setTextColor(Color.WHITE);
                positiveButton.setBackgroundResource(R.drawable.bg_btn_primary);
                positiveButton.setPadding(40, 20, 40, 20);
                android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                params.leftMargin = 20;
                positiveButton.setLayoutParams(params);
            }

            if (negativeButton != null) {
                negativeButton.setTextSize(16);
                negativeButton.setTextColor(Color.WHITE);
                negativeButton.setBackgroundResource(R.drawable.bg_btn_secondary);
                negativeButton.setPadding(40, 20, 40, 20);
                negativeButton.setFocusable(true);
                negativeButton.requestFocus();
                android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                params.leftMargin = 30;
                negativeButton.setLayoutParams(params);
            }

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog);
            }
        });

        dialog.show();
        return dialog;
    }

    public static AlertDialog showPlaylistPickerDialog(Context context, String title, String[] playlistNames, DialogInterface.OnClickListener onItemClick) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setItems(playlistNames, onItemClick);
        builder.setNegativeButton("取消", null);

        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog);
            }
            Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (negativeButton != null) {
                negativeButton.setTextSize(16);
                negativeButton.setTextColor(Color.WHITE);
                negativeButton.setBackgroundResource(R.drawable.bg_btn_secondary);
                negativeButton.setPadding(40, 20, 40, 20);
                negativeButton.setFocusable(true);
                negativeButton.requestFocus();
                android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                params.leftMargin = 30;
                negativeButton.setLayoutParams(params);
            }
        });

        dialog.show();
        return dialog;
    }

    public static AlertDialog showDeleteConfirmDialog(Context context, String songName, IDialogCallback callback) {
        return showConfirmDialog(context, "删除歌曲", "确定要从歌单中删除《" + songName + "》吗？", "删除", "取消", callback);
    }

    public static AlertDialog showOverwriteConfirmDialog(Context context, String playlistName, IDialogCallback callback) {
        return showConfirmDialog(context, "歌单已存在", "已存在名为「" + playlistName + "」的歌单，是否覆盖？", "覆盖", "取消", callback);
    }

    public static androidx.appcompat.app.AlertDialog showQrCodeDialog(Context context, String title, String hint, String qrCodeUrl, String ipAddress) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_scan_search, null);

        TextView tvTitle = dialogView.findViewById(R.id.tvDialogTitle);
        TextView tvHint = dialogView.findViewById(R.id.tvDialogHint);
        ImageView ivQrCode = dialogView.findViewById(R.id.ivQrCode);
        TextView tvIpAddress = dialogView.findViewById(R.id.tvIpAddress);

        tvTitle.setText(title);
        tvHint.setText(hint);
        tvIpAddress.setText(ipAddress);

        android.graphics.Bitmap qrBitmap = generateQrCodeBitmap(qrCodeUrl, 512);
        if (qrBitmap != null) {
            ivQrCode.setImageBitmap(qrBitmap);
        }

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(context);
        builder.setView(dialogView);

        androidx.appcompat.app.AlertDialog dialog = builder.create();

        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog);
            }
        });

        return dialog;
    }

    public static androidx.appcompat.app.AlertDialog showAboutDialog(Context context) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_about, null);

        TextView tvDialogContent = dialogView.findViewById(R.id.tvDialogContent);
        tvDialogContent.setText("基于肉肉TV 开发的 Android TV 音乐播放器。\n\n支持 D-Pad 遥控器操作、专辑封面毛玻璃背景、实时歌词同步、歌单管理等功能。\n\n项目地址：\nhttps://github.com/boluofan/music-tv");

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(context);
        builder.setView(dialogView);
        builder.setPositiveButton("确定", null);

        androidx.appcompat.app.AlertDialog dialog = builder.create();

        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog);
            }
            Button positiveButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            if (positiveButton != null) {
                positiveButton.setTextSize(16);
                positiveButton.setTextColor(Color.WHITE);
                positiveButton.setBackgroundResource(R.drawable.bg_btn_primary);
                positiveButton.setPadding(40, 20, 40, 20);
                positiveButton.setFocusable(true);
                positiveButton.requestFocus();
            }
        });

        dialog.show();
        return dialog;
    }

    private static android.graphics.Bitmap generateQrCodeBitmap(String content, int size) {
        try {
            com.google.zxing.BarcodeFormat format = com.google.zxing.BarcodeFormat.QR_CODE;
            com.google.zxing.EncodeHintType[] hintTypes = new com.google.zxing.EncodeHintType[]{
                com.google.zxing.EncodeHintType.CHARACTER_SET,
                com.google.zxing.EncodeHintType.MARGIN
            };
            String[] hints = new String[]{"UTF-8", "1"};
            Hashtable<com.google.zxing.EncodeHintType, String> hints2 = new Hashtable<>();
            hints2.put(com.google.zxing.EncodeHintType.CHARACTER_SET, "UTF-8");
            hints2.put(com.google.zxing.EncodeHintType.MARGIN, "1");
            
            com.google.zxing.qrcode.QRCodeWriter writer = new com.google.zxing.qrcode.QRCodeWriter();
            com.google.zxing.common.BitMatrix bitMatrix = writer.encode(content, format, size, size, hints2);
            
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.RGB_565);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bmp.setPixel(x, y, bitMatrix.get(x, y) ? android.graphics.Color.BLACK : android.graphics.Color.WHITE);
                }
            }
            return bmp;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}