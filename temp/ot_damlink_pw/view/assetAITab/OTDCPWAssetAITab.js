(function (exports) {

    let AiTagsInspectorTabView = exports.AiTagsInspectorTabView = otui.define("AiTagsInspectorTabView", ["withAccordion"], function () {

        this.properties = {
            'name': 'AI-Product-Tags',
            'title': otui.tr("AI Product Tags"),
            'assetID': undefined
        };

        this.asset = undefined;

        this._initContent = function _initContent(self, callback) {
            let template = self.getTemplate("content1");
            callback(template);
            load.call(this);
        }

        function load() {
			var acc = document.getElementById('otdcpw-accordion-group');
			if (acc) acc.setAttribute('alt', otui.tr('Open and Close accordion group'));
			
            let data = { 'assetID': this.properties.assetID, 'unwrap': true };
            let self = this;
            self.blockContent();
            otui.services.asset.read(data,
                function (asset, success) {
                    if (!success) {
                        let msg;
                        if (asset.exception_body) {
                            if (asset.exception_body.http_response_code == 404)
                                msg = otui.tr("Either the asset does not exist or you do not have permission to view the asset. Please contact your administrator.")
                            else
                                msg = asset.exception_body.message;
                        }

                        if (msg) {
                            otui.NotificationManager.showNotification({
                                'message': msg,
                                'status': 'error',
                                'stayOpen': true
                            });
                        }

                        self.callRoute("back");
                    } else {
                        self.unblockContent();
                        this.asset = asset;
                        createPreview.call(this);
                    }
                });
        }

        function createTable(header1, header2, header3) {
            let table = $("<table>").addClass("otdcpw-aitags-table");
            // Create the table header
            let thead = $("<thead>").addClass("otdcpw-aitags-table-thead");
            let headerRow = $("<tr>").addClass("otdcpw-aitags-table-thead-tr");
            let th1 = $("<th>").addClass("otdcpw-aitags-table-th");
            th1.append($("<p>").addClass("otdcpw-aitags-table-th-p otdcpw-align-left").text(header1));
            headerRow.append(th1);
            let th2 = $("<th>").addClass("otdcpw-aitags-table-th");
            th2.append($("<p>").addClass("otdcpw-aitags-table-th-p otdcpw-align-left").text(header2));
            headerRow.append(th2);
            let th3 = $("<th>").addClass("otdcpw-aitags-table-th");
            th3.append($("<p>").addClass("otdcpw-aitags-table-th-p otdcpw-align-right").text(header3));
            headerRow.append(th3);
            thead.append(headerRow);
            table.append(thead);
            // Create the table body
            let tbody = $("<tbody>").addClass("otdcpw-aitags-table-tbody");
            table.append(tbody);
            return table;
        }

        function createAccordion(headerText) {
            let accordion = $("<div>").addClass("otdcpw-border-bottom").attr("id","parent_"+headerText);
            let header = $("<div>").addClass("otdcpw-accordion-header").append($("<span>").addClass("otdcpw-accordion-header-title").text(headerText)).attr("tabindex","0");
            header.keydown(function (event) {
                if(event.key === 'Enter') {
                    headerClickAction(headerText);
                }
            })
            let arrow = $("<span>").addClass("otdcpw-accordion-up otdcpw-accordion-arrow").html("&#10095;").attr("id","arrow_"+headerText);
            arrow.attr("tabindex","0");
            arrow.keydown(function (event) {
                if(event.key === 'Enter') {
                    $(this).parent().click();
                }
            })
            header.append(arrow);
            let content = $("<div>").addClass("otdcpw-accordion-content").attr("id","content_"+headerText);
            accordion.append(header);
            accordion.append(content);
            return accordion;
        }

        function createAccordionWithTable(headerText, children) {
            let accordion = createAccordion(headerText);
            accordion.children().eq(1).append(children);
            return accordion;
        }

        function getConfidence(tableFieldValue, index) {
            let confidenceList = tableFieldValue.metadata_element_list[1];
            if (confidenceList?.values[index]?.value?.value) {
                return (confidenceList.values[index].value.value) + "%";
            }
            return '';
        }

        function openAllAccordions() {
            $(".otdcpw-accordion-header").each(function (index) {
                $(this).find(".otdcpw-accordion-up").each(function () {
                    if ($(this).is(':visible')) {
                        $(this).parent().click();
                    }
                });
            });
        }

        function collapseAllAccordions() {
            $(".otdcpw-accordion-header").each(function (index) {
                $(this).find(".otdcpw-accordion-down").each(function () {
                    if ($(this).is(':visible')) {
                        $(this).parent().click();
                    }
                });
            });
        }

        function openAndCloseAllAccordions() {
            let accordionGroup = $("#otdcpw-accordion-group");
            if (accordionGroup.hasClass("otdcpw-accordion-open-icon")) {
                openAllAccordions();
                accordionGroup.addClass("otdcpw-accordion-collapse-icon");
                accordionGroup.removeClass("otdcpw-accordion-open-icon");
                accordionGroup.attr("title", otui.tr("Collapse All Accordions"));
            } else if (accordionGroup.hasClass("otdcpw-accordion-collapse-icon")) {
                collapseAllAccordions();
                accordionGroup.addClass("otdcpw-accordion-open-icon");
                accordionGroup.removeClass("otdcpw-accordion-collapse-icon");
                accordionGroup.attr("title", otui.tr("Expand All Accordions"));
            }
        }

        function createPreview() {
            let categoryMap = new Map();
            let colorMap = new Map();
            let fashionTagging = true;
            $.each(this.asset.metadata.metadata_element_list, function (mainindex, modelValue) {
                if (modelValue.id === 'OTDCAI.FG.FASHION.TAGS') {
                    fashionTagging = true;
                    $.each(modelValue.metadata_element_list, function (tableFieldIndex, tableFieldValue) {
                        if (tableFieldValue.id === 'OTDCAI.TBL.FASHION.TAGS') {
                            $.each(tableFieldValue.metadata_element_list, function (tableFieldValueIndex, tableFieldValueList) {
                                if (tableFieldValueList.id === 'OTDCAI.TBLFLD.FASHION.TAGS') {
                                    $.each(tableFieldValueList.values, function (rowIndex, rowValue) {
                                        let category = '';
                                        $.each(rowValue.value.element_values.entry, function (entryIndex, entryValue) {
                                            if (entryIndex === 0) {
                                                category = entryValue.value.display_value;
                                                if (!categoryMap.has(entryValue.value.display_value)) {
                                                    categoryMap.set(entryValue.value.display_value, createTable(otui.tr("Attribute"), otui.tr("Value"), otui.tr("Confidence")));
                                                }
                                            } else {
                                                let categoryTable = categoryMap.get(category);
                                                let row = $("<tr>").addClass("otdcpw-aitags-table-tbody-tr");
                                                row.append($("<td>").addClass("otdcpw-aitags-table-tbody-td otdcpw-align-left otdcpw-width40").text(rowValue?.value?.element_values?.entry[entryIndex]?.value?.display_value));
                                                row.append($("<td>").addClass("otdcpw-aitags-table-tbody-td otdcpw-align-left otdcpw-width40").text(rowValue?.value?.element_values?.entry[entryIndex + 1]?.value?.display_value));
                                                row.append($("<td>").addClass("otdcpw-aitags-table-tbody-td otdcpw-align-right otdcpw-width20").text(getConfidence(tableFieldValue, rowIndex)));
                                                categoryTable.children().eq(1).append(row);
                                                return false;
                                            }
                                        });
                                    });
                                }
                            });
                        } else if (tableFieldValue.id === 'OTDCAI.TBL.COLOR') {
                            let categoryColors = [];
                            if (tableFieldValue.metadata_element_list[0].id === 'OTDCAI.TBLFLD.COLOR.CATEGORY') {
                                $.each(tableFieldValue.metadata_element_list[0].values, function (valueIndex, value) {
                                    categoryColors.push({
                                        category: value.value.display_value,
                                        baseColor: tableFieldValue.metadata_element_list[1].values[valueIndex]?.value?.element_values?.entry[0]?.value?.display_value,
                                        color: tableFieldValue.metadata_element_list[1].values[valueIndex]?.value?.element_values?.entry[1]?.value?.display_value,
                                        hex: tableFieldValue.metadata_element_list[2]?.values[valueIndex]?.value?.value
                                    });
                                });
                            }
                            if (categoryColors.length !== 0) {
                                categoryColors.forEach(category => {
                                    if (colorMap.has(category.category)) {
                                        let colorTable = colorMap.get(category.category);
                                        let row = $("<tr>").addClass("otdcpw-aitags-table-tbody-tr");
                                        row.append($("<td>").addClass("otdcpw-aitags-table-tbody-td otdcpw-align-left otdcpw-width40").text(category.baseColor));
                                        row.append($("<td>").addClass("otdcpw-aitags-table-tbody-td otdcpw-align-left otdcpw-width40").text(category.color));
                                        let colorHex = $("<td>").addClass("otdcpw-aitags-table-tbody-td otdcpw-align-right otdcpw-width20");
                                        if (category.hex) {
                                            colorHex.append($("<span>").addClass("otdcpw-inline").text(category.hex));
                                            colorHex.append($("<span>").addClass("otdcpw-inline otdcpw-hex-color").css('background-color', category.hex));
                                        }
                                        row.append(colorHex);
                                        colorTable.children().eq(1).append(row);
                                        colorMap.set(category.category, colorTable);
                                    } else {
                                        let colorTable = createTable(otui.tr('Base Color'), otui.tr('Color'), otui.tr('Hex Code'));
                                        colorTable.addClass('otdc-margin');
                                        let row = $("<tr>").addClass("otdcpw-aitags-table-tbody-tr");
                                        row.append($("<td>").addClass("otdcpw-aitags-table-tbody-td otdcpw-align-left otdcpw-width40").text(category.baseColor));
                                        row.append($("<td>").addClass("otdcpw-aitags-table-tbody-td otdcpw-align-left otdcpw-width40").text(category.color));
                                        let colorHex = $("<td>").addClass("otdcpw-aitags-table-tbody-td otdcpw-align-right otdcpw-width20");
                                        if (category.hex) {
                                            colorHex.append($("<span>").addClass("otdcpw-inline").text(category.hex));
                                            colorHex.append($("<span>").addClass("otdcpw-inline otdcpw-hex-color").css('background-color', category.hex));
                                        }
                                        row.append(colorHex);
                                        colorTable.children().eq(1).append(row);
                                        colorMap.set(category.category, colorTable);
                                    }
                                })
                            }
                        }
                    });
                } else if (modelValue.id === 'OTDCAI.FG.GROCERY.TAGS') {
                    fashionTagging = false;
                    createGroceryTable(modelValue);
                }
            });

            if (fashionTagging) {
                if (categoryMap.size === 0) {
                    previewNoTagsView();
                } else {
                    $('#otdcpw-accordion-group').show();
                    hideNoTagsViewbuttons();
                    categoryMap.forEach(function (value, key) {
                        let accordion = createAccordionWithTable(key, value);
                        if (colorMap.has(key)) {
                            let value = colorMap.get(key);
                            accordion.children().eq(1).append(value);
                        }
                        $('.otdcpw-accordion').append(accordion);
                    });
                    assignAccordionActions();
                }
            }
        }

        function previewNoTagsView() {
            let noTagsElement = $('.otdcpw-accordion');
            noTagsElement.empty();
            noTagsElement.append($('<div>').addClass('otdcpw-noTagsIcon'));
            noTagsElement.append($('<p>').text(otui.tr('No AI tags have been created.')).addClass('otdcpw-noTagsText'));
            $('#otdcpw-accordion-group').hide();
            let asset = this.asset;
            if(otui.UserFETManager.isTokenAvailable("OTDCAI.ASSET.GENERATEAITAGS")){
                $('.otdcpw-generateAiTagsButton').click(function () {
                    let view = otui.Views.containing(this);
                    OTDCPWAssetActionsManager.analyseTheAsset(asset, view, load, false);
                });
                $('.otdcpw-generateAiTagsButton').show();
            } else {
                $('.otdcpw-generateAiTagsButton').hide();
            }
        }

        function hideNoTagsViewbuttons() {
            $('.otdcpw-generateAiTagsButton').hide();
            $('.otdcpw-noTagsIcon').hide();
            $('.otdcpw-noTagsText').hide();
        }

        function headerClickAction (headerText) {
            let arrow, contentDiv, parentDiv;
            let arrowId = headerText;
            if(typeof headerText === "string"){
                arrow = $("#arrow_"+headerText);
                contentDiv =  $("#content_"+headerText);
                parentDiv =  $("#parent_"+headerText);
            } else {
                arrow = $(this).find(".otdcpw-accordion-arrow");
                arrowId = arrow?.attr('id').replace("arrow_","");
                contentDiv = $(this).next(".otdcpw-accordion-content");
                parentDiv = $(this).parent();
            }
            // Toggle the visibility of the content panel
            contentDiv.slideToggle();
            parentDiv.toggleClass("otdcpw-border-bottom");

            // Change the icon to up or down
            if(arrow?.hasClass("otdcpw-accordion-down")) {
                arrow.removeClass("otdcpw-accordion-down");
                arrow.addClass("otdcpw-accordion-up")
                arrow.attr("title", otui.tr("Expand") + " " + arrowId);
            } else if(arrow?.hasClass("otdcpw-accordion-up")) {
                arrow.removeClass("otdcpw-accordion-up");
                arrow.addClass("otdcpw-accordion-down");
                arrow.attr("title", otui.tr("Collapse") + " " + arrowId);
            }
        }

        function assignAccordionActions() {
            $(".otdcpw-accordion-header").click(headerClickAction);
            openAndCloseAllAccordions();
            $(".otdcpw-accordion-header").click();
            $("#otdcpw-accordion-group").addClass("otdcpw-accordion-collapse-icon");
            $("#otdcpw-accordion-group").attr("tabindex", "0").attr("role", "button").attr("title", otui.tr("Collapse all accordions"));
            $("#otdcpw-accordion-group").click(function () {
                openAndCloseAllAccordions();
            });
            $("#otdcpw-accordion-group").keydown(function (event) {
                if (event.key === 'Enter') {
                    openAndCloseAllAccordions();
                }
            });
        }

        function createGroceryTable(modelValue) {
            let result = [];
            let category = '';

            modelValue.metadata_element_list.forEach(element => {
                if (element.id === 'OTDCAI.GROCERY.CATEGORY') {
                    category = element.value?.value?.display_value;
                }
                else if (element.id === 'OTDCAI.TBL.GROCERY.TAGS') {
                    // Getting the metadata elements
                    let metadataElements = element.metadata_element_list;
                    let attributes = [];
                    let details = [];
                    let confidences = [];

                    // Separating out the attributes, details, and confidence fields based on IDs
                    metadataElements.forEach(element => {
                        if (element.id === "OTDCAI.TBLFLD.GROCERY.TAGATTR") {
                            attributes = element.values;
                        } else if (element.id === "OTDCAI.TBLFLD.GROCERY.TAGVAL") {
                            details = element.values;
                        } else if (element.id === "OTDCAI.TBLFLD.GROCERY.TAGPRED") {
                            confidences = element.values;
                        }
                    });

                    // Constructing the final result
                    attributes.forEach((attr, index) => {
                        let attrValue = attr.value.value;
                        let detailValue = details[index].value.value;
                        let confidenceValue = confidences[index].value.value;

                        // Checking if the attribute is already present in the result
                        let existingItem = result.find(item => item.attribute === attrValue);

                        if (existingItem) {
                            existingItem.values.push({
                                "value": detailValue,
                                "confidence": confidenceValue
                            });
                        } else {
                            result.push({
                                "attribute": attrValue,
                                "values": [
                                    {
                                        "value": detailValue,
                                        "confidence": confidenceValue
                                    }
                                ]
                            });
                        }
                    });
                }
            });

            //Check if Category is available in inherited fields
            if (!category) {
                asset.inherited_metadata_collections?.forEach(container => {
                    container.inherited_metadata_values?.forEach(field => {
                        if (field.id === 'OTDCAI.GROCERY.CATEGORY') {
                            category = field.metadata_element?.value?.value?.display_value;
                        }
                    });
                });
            }
            if (!category || result.length === 0) {
                previewNoTagsView();
            } else {
                buildGroceryTable(category, result);
                hideNoTagsViewbuttons();
            }
        }

        function buildGroceryTable(category, result) {
            let accordion = createAccordion(category);
            let table = createTable(otui.tr("Attribute"), otui.tr("Value"), otui.tr("Confidence"));

            result.forEach((item, itemIndex) => {
                const attribute = item.attribute;
                let valuesLength = item.values.length;

                item.values.forEach((valueObj, index) => {
                    //create row
                    const $row = $("<tr>").addClass("otdcpw-aitags-table-tbody-tr").attr("id", itemIndex + "_" + index);
                    if(index > 0){
                        $row.hide();
                    }

                    // Add attribute only to the first row of the group with rowspan
                    if (index === 0) {
                        let $td = $("<td>").attr("rowspan", 1)
                            .attr("id", itemIndex + "_" + index + "_attribute").addClass("otdcpw-aitags-table-tbody-td otdcpw-align-left otdcpw-width40")
                            .text(attribute);
                        if (valuesLength > 1) {
                            let $a = $("<a>").addClass("otdc-aitags-table-show-more-less")
                                .text(otui.tr("Show more"))
                                .on("click", function () {
                                    let column = $(`#${itemIndex}_0_attribute`);
                                    if (column.attr('rowspan') > 1) {
                                        column.attr('rowspan', 1);
                                        this.text = otui.tr("Show more");
                                    } else {
                                        column.attr('rowspan', valuesLength);
                                        this.text = otui.tr("Show less");
                                    }
                                    for (let i = 1; i < item.values.length; i++) {
                                        let row = $(`#${itemIndex}_${i}`);
                                        if (row.css("display") === "none") {
                                            row.show();
                                        } else {
                                            row.hide();
                                        }
                                    }
                                });
                            $td.append(' (');
                            $td.append($a);
                            $td.append(')');
                        }
                        $row.append($td);
                    }

                    //add rest of the columns
                    $row.append($("<td>").addClass("otdcpw-aitags-table-tbody-td otdcpw-align-left otdcpw-width40").text(valueObj.value));
                    $row.append($("<td>").addClass("otdcpw-aitags-table-tbody-td otdcpw-align-right otdcpw-width40").text(valueObj.confidence + "%"));

                    //append to tbody
                    table.children().eq(1).append($row);
                });
            });
            accordion.children().eq(1).append(table);
            $('.otdcpw-accordion').append(accordion);
            assignAccordionActions();
        }
    });

    otui.augment("InspectorView", function () {
        this.hierarchy.push({
            'view': AiTagsInspectorTabView,
            'if': function () {
                const urlParams = new URLSearchParams(window.location.search);
                const param1 = urlParams.get('p');
                return otui.UserFETManager.isTokenAvailable("OTDCAI.VIEW.AITAGS") && param1.startsWith('otdc') && OTDCUtilities.isPWContext();
            },
            'properties': {
                'assetID': this.inherit('assetID')
            }
        });
    });
})(window);