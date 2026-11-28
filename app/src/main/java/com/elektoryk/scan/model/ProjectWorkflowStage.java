package com.elektoryk.scan.model;

public enum ProjectWorkflowStage {
    SETUP("Обмір / ТЗ", "Задати базові параметри об'єкта"),
    SCAN("Скан", "Зібрати приміщення телефоном"),
    PLAN("План", "Побудувати і перевірити Multi-room геометрію"),
    ELECTRICAL("Електрика", "Розставити точки, щит і групи"),
    ROUTING("Траси", "Порахувати маршрути і кабель"),
    MATERIALS("Матеріали", "Підготувати CUT-лист, бухти і захист"),
    COMMERCIAL("Кошторис / закупівля", "Задати ціни, склад, закупівлю і роботи"),
    INSTALLATION("Монтаж", "Нарізати, промаркувати і змонтувати"),
    QUALITY("Перевірка", "Підтвердити точність, електроплан і commissioning"),
    AS_BUILT("As-built", "Зберегти фото прихованих робіт і фінальний фактичний стан"),
    HANDOVER("Здача", "Сформувати VIP паспорт, погодження і цифровий пакет");

    public final String label;
    public final String goal;
    ProjectWorkflowStage(String label,String goal){this.label=label;this.goal=goal;}
}
