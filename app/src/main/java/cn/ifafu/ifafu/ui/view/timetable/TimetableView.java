package cn.ifafu.ifafu.ui.view.timetable;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.widget.TextViewCompat;
import androidx.gridlayout.widget.GridLayout;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import cn.ifafu.ifafu.R;
import cn.ifafu.ifafu.util.ColorPool;
import cn.ifafu.ifafu.util.DensityUtils;
import cn.ifafu.ifafu.util.LightColorPool;

/**
 * 为确保老版本设备兼容性
 * 继承于{@link androidx.gridlayout.widget.GridLayout}
 * 而非{@link android.widget.GridLayout}
 */
public class TimetableView extends GridLayout {

    private final Map<TimetableItem, TextView> itemViewMap = new HashMap<>();

    private final float    dateLayoutHeightWeight = 1.08F;
    private final float    sideLayoutWidthWeight  = 0.72F;
    private final String[] dayOfWeekCN            = {"日", "一", "二", "三", "四", "五", "六"};
    private final int      dp1                    = DensityUtils.dp2px(getContext(), 1F);
    private final int      dp4                    = DensityUtils.dp2px(getContext(), 4F);
    private final int      dp8                    = DensityUtils.dp2px(getContext(), 8F);
    private final int      dp10                   = DensityUtils.dp2px(getContext(), 10F);

    private TextView         cornerTextView;
    private LinearLayout[]   weekLayouts;
    private RelativeLayout[] noteLayouts;

    private String[] timeTexts;
    private String[] dateTexts;
    private int todayColumn = -1;
    private final Set<String> urgentItemKeys = new HashSet<>();

    //配置类
    private Config                  config = new Config();
    private OnItemClickListener     clickListener;
    private OnItemLongClickListener longClickListener;

    public TimetableView(Context context) {
        this(context, null);
    }

    public TimetableView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TimetableView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setBackgroundColor(resolveColor(com.google.android.material.R.attr.colorSurface, Color.WHITE));
        setClipToPadding(false);
        initCornerLayout();
        initNodeLayout();
        initWeekLayout();
    }

    @NotNull
    public Config getConfig() {
        try {
            return config.clone();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
            return new Config();
        }
    }

    public void setConfig(Config config) {
        Config previous = this.config;
        this.config = config;
        if (previous.totalNodeCount != config.totalNodeCount) {
            removeAllViews();
            initWeekLayout();
            initNodeLayout();
            initCornerLayout();
            initTimeTextView();
            itemViewMap.clear();
        } else {
            if (previous.itemTextSize != config.itemTextSize) {
                for (TextView view : itemViewMap.values()) {
                    TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                            view,
                            7,
                            Math.max(8, Math.min(11, (int) config.itemTextSize)),
                            1,
                            TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM
                    );
                }
            }
            if (previous.showHorizontalLine != config.showHorizontalLine ||
                    previous.showVerticalLine != config.showVerticalLine) {
                invalidate();
            }
            if (previous.otherTextColor != config.otherTextColor) {
                int textColor = config.otherTextColor;
                for (RelativeLayout layout : noteLayouts) {
                    TextView timeTextView = layout.findViewById(R.id.id_time_textview);
                    if (timeTextView != null) {
                        timeTextView.setTextColor(textColor);
                    }
                    TextView nodeTextView = layout.findViewById(R.id.id_node_textview);
                    if (nodeTextView != null) {
                        nodeTextView.setTextColor(textColor);
                    }
                }
                for (LinearLayout layout : weekLayouts) {
                    TextView weekTextView = layout.findViewById(R.id.id_week_textview);
                    if (weekTextView != null) {
                        weekTextView.setTextColor(textColor);
                    }
                    TextView dateTextView = layout.findViewById(R.id.id_date_textview);
                    if (dateTextView != null) {
                        dateTextView.setTextColor(textColor);
                    }
                }
                cornerTextView.setTextColor(textColor);
            }
            if (previous.showDate != config.showDate) {
                int visibility;
                if (config.showDate) {
                    visibility = View.VISIBLE;
                } else {
                    visibility = View.GONE;
                }
                for (LinearLayout layout : weekLayouts) {
                    TextView dateTextView = layout.findViewById(R.id.id_date_textview);
                    if (dateTextView != null) {
                        dateTextView.setVisibility(visibility);
                    }
                }
            }
            if (previous.showTime != config.showTime) {
                int visibility;
                if (config.showTime) {
                    visibility = View.VISIBLE;
                } else {
                    visibility = View.GONE;
                }
                for (RelativeLayout layout : noteLayouts) {
                    TextView timeTextView = layout.findViewById(R.id.id_time_textview);
                    if (timeTextView != null) {
                        timeTextView.setVisibility(visibility);
                    }
                }
            }
        }
    }

    public void setItemClickListener(final OnItemClickListener listener) {
        if (listener != clickListener) {
            clickListener = listener;
            for (Map.Entry<TimetableItem, TextView> e : itemViewMap.entrySet()) {
                e.getValue().setOnClickListener(v -> clickListener.onClick(e.getValue(), e.getKey()));
            }
        }
    }

    public void setItemLongClickListener(final OnItemLongClickListener listener) {
        if (listener != longClickListener) {
            longClickListener = listener;
            for (Map.Entry<TimetableItem, TextView> e : itemViewMap.entrySet()) {
                e.getValue().setOnLongClickListener(v -> {
                    longClickListener.onLongClick(e.getValue(), e.getKey());
                    return true;
                });
            }
        }
    }

    public void setMonth(int month) {
        if (month < 1 || month > 12) {
            cornerTextView.setText("");
        } else {
            cornerTextView.setText((month + "\n月"));
        }
    }

    public void setTimeText(String[] timeTexts) {
        this.timeTexts = timeTexts;
        initTimeTextView();
    }

    public void setDateTexts(String[] dateTexts) {
        if (this.dateTexts == dateTexts) {
            return;
        }
        this.dateTexts = dateTexts;
        for (int i = 0; i < weekLayouts.length; i++) {
            TextView dateTextView = weekLayouts[i].findViewById(R.id.id_date_textview);
            if (dateTextView == null) {
                dateTextView = new TextView(getContext());
                dateTextView.setId(R.id.id_date_textview);
                dateTextView.setGravity(Gravity.CENTER);
                dateTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                dateTextView.setTextColor(config.otherTextColor);
                dateTextView.setMinWidth(DensityUtils.dp2px(getContext(), 22F));
                dateTextView.setMinHeight(DensityUtils.dp2px(getContext(), 19F));
                dateTextView.setPadding(dp4, 0, dp4, 0);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                weekLayouts[i].addView(dateTextView, ilp);
            }
            if (dateTexts == null) {
                dateTextView.setVisibility(View.GONE);
            } else {
                dateTextView.setVisibility(View.VISIBLE);
                dateTextView.setText(dateTexts[i]);
                dateTextView.setTypeface(null, i == todayColumn ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
                dateTextView.setTextColor(i == todayColumn
                        ? resolveColor(com.google.android.material.R.attr.colorPrimary, config.otherTextColor)
                        : config.otherTextColor);
            }
        }
    }

    /** Marks today's column in the week header; -1 clears the marker. */
    public void setTodayColumn(int column) {
        todayColumn = column;
        if (weekLayouts == null) return;
        for (int i = 0; i < weekLayouts.length; i++) {
            TextView week = weekLayouts[i].findViewById(R.id.id_week_textview);
            TextView date = weekLayouts[i].findViewById(R.id.id_date_textview);
            boolean today = i == todayColumn;
            if (week != null) {
                week.setBackground(createHeaderBackground());
                week.setTypeface(null, today ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
                week.setTextColor(today
                        ? resolveColor(com.google.android.material.R.attr.colorPrimary, config.otherTextColor)
                        : config.otherTextColor);
            }
            if (date != null) {
                date.setBackground(today ? createTodayHeaderBackground() : null);
                date.setTypeface(null, today ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
                date.setTextColor(today
                        ? resolveColor(com.google.android.material.R.attr.colorOnPrimary, Color.WHITE)
                        : config.otherTextColor);
            }
        }
        invalidate();
    }

    public void addItems(final Collection<TimetableItem> items) {
        for (TimetableItem item : items) {
            addItem(item);
        }
    }

    public void addItem(final TimetableItem item) {
        TextView itemView = new TextView(getContext());
        itemView.setPadding(dp1, dp1, dp1, dp1);
        itemView.setGravity(Gravity.CENTER);
        itemView.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        itemView.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.min(11F, config.itemTextSize));
        itemView.getPaint().setFakeBoldText(true);
        itemView.setLineSpacing(0F, 0.92F);
        String text = item.name;
        if (item.address != null && !item.address.isEmpty()) {
            text += "\n" + item.address;
        }
        int courseColor = config.colorPool.getColor(item.name);
        itemView.setText(text);
        itemView.setMaxLines(Math.max(2, Math.min(6, item.nodeCount * 2)));
        itemView.setEllipsize(TextUtils.TruncateAt.END);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                itemView,
                7,
                Math.max(8, Math.min(11, (int) config.itemTextSize)),
                1,
                TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM
        );
        itemView.setTag(item);
        applyCourseAppearance(item, itemView, courseColor);
        itemView.setPadding(dp4, dp4, dp4, dp4);
        itemView.setContentDescription(text);
        if (clickListener != null) {
            itemView.setOnClickListener(v -> clickListener.onClick(v, item));
        }
        if (longClickListener != null) {
            itemView.setOnLongClickListener(v -> {
                longClickListener.onLongClick(v, item);
                return true;
            });
        }
        LayoutParams lp = new LayoutParams(
                spec(item.startNode, item.nodeCount, 1F),
                spec(item.dayOfWeek, 1, 1F)
        );
        lp.height = 0;
        lp.width = 0;
        lp.setMargins(dp1, dp1, dp1, dp1);
        itemViewMap.put(item, itemView);
        addView(itemView, lp);
    }

    /** Material error-container treatment for a course starting in under 15 minutes. */
    public void setUrgentItemKeys(@Nullable Collection<String> keys) {
        urgentItemKeys.clear();
        if (keys != null) urgentItemKeys.addAll(keys);
        for (Map.Entry<TimetableItem, TextView> entry : itemViewMap.entrySet()) {
            int courseColor = config.colorPool.getColor(entry.getKey().name);
            applyCourseAppearance(entry.getKey(), entry.getValue(), courseColor);
        }
    }

    public static String itemKey(TimetableItem item) {
        return item.name + '|' + item.dayOfWeek + '|' + item.startNode;
    }

    private void applyCourseAppearance(TimetableItem item, TextView view, int courseColor) {
        boolean urgent = urgentItemKeys.contains(itemKey(item));
        int background = urgent
                ? resolveColor(com.google.android.material.R.attr.colorErrorContainer, 0xFFFFDAD6)
                : courseColor;
        int foreground = urgent
                ? resolveColor(com.google.android.material.R.attr.colorOnErrorContainer, 0xFF410002)
                : resolveColor(com.google.android.material.R.attr.colorOnSurface, contrastColor(background));
        view.setTextColor(foreground);
        view.setBackground(createCourseBackground(background, urgent));
    }

    public void setItems(@Nullable final Collection<TimetableItem> items) {
        for (TextView itemView : itemViewMap.values()) {
            removeView(itemView);
        }
        itemViewMap.clear();
        if (items != null) {
            addItems(items);
        }
    }

    public void removeItem(TimetableItem item) {
        TextView itemView = itemViewMap.get(item);
        if (itemView != null) {
            itemViewMap.remove(item);
            removeView(itemView);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (config.showHorizontalLine || config.showVerticalLine) {
            Paint paint = new Paint();
            int outline = resolveColor(com.google.android.material.R.attr.colorOutline, 0xFF757577);
            paint.setColor(withAlpha(outline, 0x24));
            paint.setStrokeWidth(Math.max(dp1, 1));
            paint.setStyle(Paint.Style.STROKE);

            //获取跟布局的宽高
            final float availableWidth = super.getRight() - super.getLeft();
            final float availableHeight = super.getBottom() - super.getTop();

            if (config.showHorizontalLine) {
                float totalHeightWeight = dateLayoutHeightWeight + config.totalNodeCount;
                float perItemHeight = 1F / totalHeightWeight * availableHeight;
                //第一条水平分割线的y轴位置
                float y = dateLayoutHeightWeight / totalHeightWeight * availableHeight;
                for (int i = 0; i < config.totalNodeCount; i++) {
                    canvas.drawLine(0F, y, availableWidth, y, paint);
                    y += perItemHeight;
                }
            }

            if (config.showVerticalLine) {
                paint.setColor(withAlpha(outline, 0x16));
                float totalWidthWeight = sideLayoutWidthWeight + 7F;
                float perItemWidth = 1F / totalWidthWeight * availableWidth;
                //第一条垂直分割线的y轴位置
                float x = sideLayoutWidthWeight / totalWidthWeight * availableWidth;
                for (int i = 0; i < 7; i++) {
                    canvas.drawLine(x, 0F, x, availableHeight, paint);
                    x += perItemWidth;
                }
            }
        }

        super.dispatchDraw(canvas);
    }

    private void initNodeLayout() {
        noteLayouts = new RelativeLayout[config.totalNodeCount];
        RelativeLayout.LayoutParams ilp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        ); // 可复用，无需重复new
        ilp.addRule(RelativeLayout.CENTER_IN_PARENT);
        for (int row = 0; row < config.totalNodeCount; row++) {
            RelativeLayout layout = new RelativeLayout(getContext());
            //添加节数TextView
                TextView nodeTextView = new TextView(getContext());
                nodeTextView.setId(R.id.id_node_textview);
                nodeTextView.setGravity(Gravity.CENTER);
                nodeTextView.setTextColor(config.otherTextColor);
                nodeTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12F);
                nodeTextView.getPaint().setFakeBoldText(true);
                nodeTextView.setText(String.valueOf(row + 1));
                nodeTextView.setBackground(createHeaderBackground());
            layout.addView(nodeTextView, ilp);
            noteLayouts[row] = layout;
            LayoutParams lp = getDefaultLayoutParams(
                    row + 1, 0, 1F, sideLayoutWidthWeight);
            addView(layout, lp);
        }
    }

    private void initTimeTextView() {
        if (timeTexts == null || noteLayouts == null) {
            return;
        }
        RelativeLayout.LayoutParams ilp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );// 可复用，无需重复new
        ilp.addRule(RelativeLayout.CENTER_HORIZONTAL);
        ilp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        int loop = Math.min(timeTexts.length, noteLayouts.length);
        for (int i = 0; i < loop; i++) {
            AppCompatTextView timeTextView = noteLayouts[i].findViewById(R.id.id_time_textview);
            if (timeTextView == null) {
                timeTextView = new AppCompatTextView(getContext());
                timeTextView.setId(R.id.id_time_textview);
                timeTextView.setMaxLines(1);
                timeTextView.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                timeTextView.setTextColor(config.otherTextColor);
                timeTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9F);
                //设置兼容低版本的自适应字体大小
                TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                        timeTextView,
                        5,
                        9,
                        1,
                        TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM
                );
                noteLayouts[i].addView(timeTextView, ilp);
            }
            timeTextView.setText(timeTexts[i]);
        }
    }

    private void initWeekLayout() {
        weekLayouts = new LinearLayout[7];
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                DensityUtils.dp2px(getContext(), 20F)
        ); // 可复用，无需重复new
        for (int column = 0; column < 7; column++) {
            LinearLayout ll = new LinearLayout(getContext());
            ll.setOrientation(LinearLayout.VERTICAL);
            ll.setGravity(Gravity.CENTER);
            ll.setPadding(dp1, 0, dp1, 0);
            TextView weekTextView = new TextView(getContext());
            weekTextView.setId(R.id.id_week_textview);
            weekTextView.setTextColor(config.otherTextColor);
            weekTextView.setGravity(Gravity.CENTER);
            weekTextView.setText(dayOfWeekCN[column]);
            weekTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12F);
            weekTextView.getPaint().setFakeBoldText(true);
            weekTextView.setBackground(createHeaderBackground());
            ll.addView(weekTextView, ilp);
            weekLayouts[column] = ll;
            LayoutParams lp = getDefaultLayoutParams(
                    0, column + 1, dateLayoutHeightWeight, 1F);
            addView(ll, lp);
        }
    }

    private void initCornerLayout() {
        cornerTextView = new TextView(getContext());
        cornerTextView.setGravity(Gravity.CENTER);
        cornerTextView.getPaint().setFakeBoldText(true);
        cornerTextView.setTextColor(config.otherTextColor);
        cornerTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11F);
        cornerTextView.setPadding(dp1, dp1, dp1, dp1);
        cornerTextView.setBackground(createHeaderBackground());
        LayoutParams lp = getDefaultLayoutParams(0, 0, dateLayoutHeightWeight, sideLayoutWidthWeight);
        addView(cornerTextView, lp);
    }

    private LayoutParams getDefaultLayoutParams(int row, int column, float heightWeight, float widthWeight) {
        LayoutParams lp = new LayoutParams(
                spec(row, 1, heightWeight),
                spec(column, 1, widthWeight));
        lp.width = 0;
        lp.height = 0;
        return lp;
    }

    private GradientDrawable createCourseBackground(int color, boolean urgent) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(DensityUtils.dp2px(getContext(), 15F));
        if (urgent) {
            drawable.setStroke(
                    DensityUtils.dp2px(getContext(), 2F),
                    resolveColor(com.google.android.material.R.attr.colorError, 0xFFBA1A1A)
            );
        }
        return drawable;
    }

    private GradientDrawable createHeaderBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.TRANSPARENT);
        return drawable;
    }

    private GradientDrawable createTodayHeaderBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(resolveColor(com.google.android.material.R.attr.colorPrimary, 0xFF6750A4));
        drawable.setCornerRadius(DensityUtils.dp2px(getContext(), 100F));
        return drawable;
    }

    private int resolveColor(int attr, int fallback) {
        try {
            return com.google.android.material.color.MaterialColors.getColor(this, attr, fallback);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private int contrastColor(int color) {
        double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color)
                + 0.114 * Color.blue(color)) / 255D;
        return luminance > 0.68 ? 0xFF1D1B20 : Color.WHITE;
    }

    public interface OnItemClickListener {
        void onClick(View v, TimetableItem item);
    }

    public interface OnItemLongClickListener {
        void onLongClick(View v, TimetableItem item);
    }

    /**
     * 配置类
     */
    public static class Config implements Cloneable {
        public float     itemTextSize       = 12F;
        public int       totalNodeCount     = 12;
        public int       otherTextColor     = Color.BLACK;
        public boolean   showVerticalLine   = true;
        public boolean   showHorizontalLine = true;
        public boolean   showDate           = true;
        public boolean   showTime           = true;
        public ColorPool colorPool          = new LightColorPool();

        @NotNull
        @Override
        protected Config clone() throws CloneNotSupportedException {
            return (Config) super.clone();
        }

    }
}
