package com.duperknight.client.gui;

import com.duperknight.client.gui.widgets.DropdownWidget;
import com.duperknight.client.modules.DepartmentRank;
import com.duperknight.client.modules.StaffDepartment;
import com.duperknight.client.modules.StaffRank;
import com.duperknight.client.utils.DMLSConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/** Persisted mod-wide settings that do not belong to an individual module. */
public final class DMLSOptionsScreen extends DMLSMenuScreen {
    private int rankX;
    private int rankY;
    private int rankWidth;
    private int departmentsX;
    private int departmentsY;
    private int departmentsWidth;
    private int dividerY;
    private int departmentDropdownY;
    private int allowedServersY;

    public DMLSOptionsScreen(Screen parent) {
        super(Text.translatable("dmls.title.options"), parent);
    }

    @Override
    protected void init() {
        rankWidth = Math.min(scaled(240), width - scaled(32));
        rankX = width / 2 - rankWidth / 2;
        int footerTop = height - FOOTER_TOP_OFFSET;
        int contentTop = HEADER_HEIGHT + scaled(25);
        int contentHeight = scaled(130);
        rankY = contentTop + Math.max(0, (footerTop - contentTop - contentHeight) / 2);

        departmentsWidth = Math.min(scaled(480), width - scaled(32));
        departmentsX = width / 2 - departmentsWidth / 2;
        dividerY = rankY + scaled(40);
        departmentsY = dividerY + scaled(14);
        departmentDropdownY = departmentsY + scaled(15);
        allowedServersY = departmentDropdownY + STANDARD_BUTTON_HEIGHT + scaled(14);
        int gap = scaled(8);
        int departmentWidth = (departmentsWidth - gap * 2) / 3;

        int index = 0;
        for (StaffDepartment department : StaffDepartment.values()) {
            int x = departmentsX + index * (departmentWidth + gap);
            addDropdownChild(DropdownWidget.builder(
                            department.displayName(),
                            DepartmentRank.optionsFor(department),
                            DMLSConfig.departmentRank(department),
                            DepartmentRank::displayName,
                            (dropdown, value) -> DMLSConfig.setDepartmentRank(department, value))
                    .dimensions(x, departmentDropdownY, departmentWidth, STANDARD_BUTTON_HEIGHT)
                    .maxVisibleRows(4)
                    .showOptionLabel(false)
                    .build());
            index++;
        }

        if (DMLSConfig.staffRank().isAtLeast(StaffRank.SENIOR_MODERATOR)) {
            addDrawableChild(ButtonWidget.builder(Text.translatable("dmls.option.allowed_servers"),
                            button -> client.setScreen(new AllowedServersScreen(this)))
                    .dimensions(rankX, allowedServersY, rankWidth, STANDARD_BUTTON_HEIGHT).build());
        }
        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> close())
                .dimensions(width / 2 - scaled(75), footerButtonY(), scaled(150), STANDARD_BUTTON_HEIGHT).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderMenuBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, HEADER_HEIGHT + scaled(16), 0xFFFFFFFF);

        renderPanel(context, rankX, rankY, rankWidth, STANDARD_BUTTON_HEIGHT);
        context.drawCenteredTextWithShadow(textRenderer, DMLSConfig.staffRank().displayName(),
                width / 2, rankY + (STANDARD_BUTTON_HEIGHT - textRenderer.fontHeight) / 2 + scaled(1), 0xFFFFFFFF);

        Text departmentsTitle = Text.translatable("dmls.option.departments");
        int titleHalfWidth = textRenderer.getWidth(departmentsTitle) / 2;
        int titlePadding = scaled(8);
        context.fill(departmentsX, dividerY,
                width / 2 - titleHalfWidth - titlePadding, dividerY + 1, PANEL_BORDER_COLOR);
        context.fill(width / 2 + titleHalfWidth + titlePadding, dividerY,
                departmentsX + departmentsWidth, dividerY + 1, PANEL_BORDER_COLOR);
        context.drawCenteredTextWithShadow(textRenderer, departmentsTitle,
                width / 2, dividerY - textRenderer.fontHeight / 2, 0xFFFFFFFF);

        int gap = scaled(8);
        int departmentWidth = (departmentsWidth - gap * 2) / 3;
        int index = 0;
        for (StaffDepartment department : StaffDepartment.values()) {
            int centerX = departmentsX + index * (departmentWidth + gap) + departmentWidth / 2;
            context.drawCenteredTextWithShadow(textRenderer, department.displayName(), centerX, departmentsY, 0xFFDDDDDD);
            index++;
        }
        super.render(context, mouseX, mouseY, delta);
    }
}
