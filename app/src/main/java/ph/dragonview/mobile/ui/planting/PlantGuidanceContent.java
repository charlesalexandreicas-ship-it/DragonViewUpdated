package ph.dragonview.mobile.ui.planting;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import ph.dragonview.mobile.R;
import ph.dragonview.mobile.data.model.PlantingStage;
import ph.dragonview.mobile.databinding.BottomSheetStageGuidanceBinding;

/** Reviewed bilingual guide mapped to existing milestone codes without changing farm records. */
final class PlantGuidanceContent {
    private PlantGuidanceContent() {}
    static int index(PlantingStage stage) {
        switch (stage) {
            case PLANTED: case ESTABLISHMENT: return 1;
            case ROOTING_AND_BUD: case SHOOT_DEVELOPMENT: return 2;
            case TRELLIS_TRAINING: case TRELLIS_REACHED: case CANOPY_DEVELOPMENT:
            case MATURE_GROWTH: return 3;
            case FLOWER_BUD: return 4;
            case FLOWERING: return 5;
            case FRUIT_SET: case FRUIT_DEVELOPMENT: return 6;
            case FRUIT_MATURATION: case READY_FOR_HARVEST: return 7;
            case HARVESTED: return 8;
            default: return 0;
        }
    }
    private static final int[] EN = {0, R.array.guidance_1_en, R.array.guidance_2_en, R.array.guidance_3_en, R.array.guidance_4_en, R.array.guidance_5_en, R.array.guidance_6_en, R.array.guidance_7_en, R.array.guidance_8_en};
    private static final int[] FIL = {0, R.array.guidance_1_fil, R.array.guidance_2_fil, R.array.guidance_3_fil, R.array.guidance_4_fil, R.array.guidance_5_fil, R.array.guidance_6_fil, R.array.guidance_7_fil, R.array.guidance_8_fil};
    // Zero means no verified photo: the image panel stays absent for stages 2 and 3.
    private static final int[] IMAGES = {0, R.drawable.guidance_stage_1, 0, 0,
            R.drawable.guidance_stage_4, R.drawable.guidance_stage_5,
            R.drawable.guidance_stage_6, R.drawable.guidance_stage_7,
            R.drawable.guidance_stage_8};
    private static final String[] IMAGE_SOURCES = {null,
            "https://commons.wikimedia.org/wiki/File:Dragon_fruit_plant.jpg", null, null,
            "https://commons.wikimedia.org/wiki/File:Hylocereus_undatus,_jard%C3%ADn_bot%C3%A1nico_de_Tallinn,_Estonia,_2012-08-13,_DD_01.JPG",
            "https://commons.wikimedia.org/wiki/File:Dragon_friut_flower.jpg",
            "https://commons.wikimedia.org/wiki/File:Hylocereus_undatus1.jpg",
            "https://commons.wikimedia.org/wiki/File:%E0%A6%A1%E0%A7%8D%E0%A6%B0%E0%A6%BE%E0%A6%97%E0%A6%A8_%E0%A6%AB%E0%A6%B2,_%E0%A6%AC%E0%A6%BE%E0%A6%82%E0%A6%B2%E0%A6%BE%E0%A6%A6%E0%A7%87%E0%A6%B6.jpg",
            "https://commons.wikimedia.org/wiki/File:Dragonfruit_Chiayi_market.jpg"};
    private static final String[] IMAGE_LICENSES = {null,
            "https://creativecommons.org/licenses/by-sa/4.0/", null, null,
            "https://creativecommons.org/licenses/by-sa/3.0/",
            "https://creativecommons.org/licenses/by-sa/4.0/",
            "https://creativecommons.org/licenses/by/3.0/",
            "https://creativecommons.org/licenses/by-sa/3.0/",
            "https://creativecommons.org/licenses/by-sa/2.0/"};
    static int imageResource(PlantingStage stage) {
        int stageIndex = index(stage);
        return stageIndex == 0 ? 0 : IMAGES[stageIndex];
    }
    static String[] get(Context context, PlantingStage stage) {
        int index = index(stage);
        return index == 0 ? null : context.getResources().getStringArray(
                filipino(context) ? FIL[index] : EN[index]);
    }
    private static boolean filipino(Context context) {
        String locale = context.getResources().getConfiguration().getLocales().get(0).getLanguage();
        return context.getSharedPreferences("plant_guidance", Context.MODE_PRIVATE)
                .getBoolean("filipino", locale.equals("fil") || locale.equals("tl"));
    }
    static void bind(BottomSheetStageGuidanceBinding sheet, PlantingStage stage) {
        Context context = sheet.getRoot().getContext();
        int stageIndex = index(stage);
        if (stageIndex == 0) {
            sheet.referenceImagePanel.setVisibility(View.GONE);
            return;
        }
        MaterialButton language = new MaterialButton(context, null,
                androidx.appcompat.R.attr.borderlessButtonStyle);
        language.setMinHeight((int) (48 * context.getResources().getDisplayMetrics().density));
        ((ViewGroup) sheet.getRoot()).addView(language, 2);
        Runnable render = () -> {
            boolean fil = filipino(context);
            String[] text = get(context, stage);
            sheet.stageTitleText.setText(text[0]);
            sheet.stageTimingText.setText(fil ? "Batay sa nakikitang paglaki; nag-iiba ang panahon" :
                    "Follow observed growth; timing varies");
            sheet.overviewText.setVisibility(View.GONE);
            sheet.expectedText.setText(text[1]);
            sheet.tasksText.setText(text[2]);
            sheet.recordText.setText(text[3]);
            sheet.warningsText.setText(text[4]);
            sheet.completionText.setText(fil ?
                    "Magpatuloy kapag nakita at naitala ang palatandaan ng susunod na yugto. Huwag ibatay sa edad lamang." :
                    "Continue when the next stage is observed and recorded. Do not rely on age alone.");
            sheet.validationText.setText(fil ?
                    "Gabay mula sa pananaliksik at extension. Iangkop sa barayti at kondisyon ng bukid." :
                    "Research and extension guidance. Adapt to the cultivar and farm conditions.");
            bindImage(sheet, stageIndex, fil, text[0]);
            String imageSources = IMAGES[stageIndex] == 0 ? "" :
                    "\n\n" + (fil ? "Pinagmulan ng larawan" : "Photo source") + "\n" +
                    IMAGE_SOURCES[stageIndex] + "\n" +
                    (fil ? "Lisensiya" : "License") + "\n" + IMAGE_LICENSES[stageIndex] +
                    "\n" + (fil ? "Walang karagdagang pag-edit sa larawan." :
                            "No further edits to the photo.");
            sheet.sourceText.setText(text[5] + imageSources + "\n\n" + SOURCES);
            android.text.util.Linkify.addLinks(sheet.sourceText, android.text.util.Linkify.WEB_URLS);
            translate(sheet.getRoot(), fil);
            sheet.closeButton.setContentDescription(fil ? "Isara ang gabay" : "Close guidance");
            language.setText(fil ? "Wika: Filipino · Switch to English" : "Language: English · Gamitin ang Filipino");
        };
        language.setOnClickListener(v -> {
            context.getSharedPreferences("plant_guidance", Context.MODE_PRIVATE).edit()
                    .putBoolean("filipino", !filipino(context)).apply();
            render.run();
        });
        render.run();
    }
    /** Shows only source-traceable stage photos and refreshes the visible credit on language changes. */
    private static void bindImage(BottomSheetStageGuidanceBinding sheet, int stageIndex,
                                  boolean filipino, String stageTitle) {
        int image = IMAGES[stageIndex];
        sheet.referenceImagePanel.setVisibility(image == 0 ? View.GONE : View.VISIBLE);
        if (image == 0) return;
        Context context = sheet.getRoot().getContext();
        sheet.referenceImage.setImageResource(image);
        sheet.referenceImage.setContentDescription((filipino ? "Halimbawang larawan: " :
                "Reference photo: ") + stageTitle);
        String[] credits = context.getResources().getStringArray(filipino ?
                R.array.guidance_image_credits_fil : R.array.guidance_image_credits_en);
        sheet.referenceImageCredit.setText(credits[stageIndex - 1]);
    }
    private static final String[][] LABELS = {
        {"Expected appearance", "Palatandaan"},
        {"What to do", "Gagawin"},
        {"What to record", "Itala"},
        {"Watch for", "Bantayan"},
        {"Ready for the next stage when…", "Kailan magpapatuloy"},
        {"Sources & validation", "Mga sanggunian"},
        {"Add optional photo", "Magdagdag ng sariling larawan"}
    };
    private static void translate(View view, boolean fil) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            for (String[] labels : LABELS) {
                if (text.getText().toString().equals(labels[0]) || text.getText().toString().equals(labels[1])) {
                    text.setText(labels[fil ? 1 : 0]);
                    break;
                }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) translate(group.getChildAt(i), fil);
        }
    }
    private static final String SOURCES =
            "Epic Gardening\nhttps://www.epicgardening.com/dragon-fruit-guide/\n\n" +
            "DA-ATI Philippines\nhttps://ati2.da.gov.ph/ati-7/content/sites/default/files/users/user16/DRAGON%20FRUIT.PDF\n\n" +
            "UF/IFAS\nhttps://ask.ifas.ufl.edu/publication/HS303\n\n" +
            "University of Guam\nhttps://www.uog.edu/_resources/files/extension/publications/Dragon_Fruit.pdf\n\n" +
            "BBCH growth stages\nhttps://doi.org/10.1016/j.scienta.2016.10.047\n\n" +
            "Pollination\nhttps://pmc.ncbi.nlm.nih.gov/articles/PMC12552510/\n\n" +
            "Maturity\nhttps://doi.org/10.1016/j.scienta.2019.04.050\nhttps://doi.org/10.1016/j.scienta.2018.08.029\n\n" +
            "Pruning\nhttps://doi.org/10.1016/j.scienta.2024.113562\n\n" +
            "Philippine stem canker\nhttps://doi.org/10.1007/s42161-020-00551-0\n\n" +
            "Disease review\nhttps://doi.org/10.1016/j.cropro.2019.104920";
}
