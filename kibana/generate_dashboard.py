"""Sinh kibana/call-analysis-dashboard.ndjson (saved objects cho Kibana 8.x)."""
import json, os

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "call-analysis-dashboard.ndjson")
DV = "call-analysis-results"

def search_source(query=""):
    return json.dumps({"query": {"query": query, "language": "kuery"}, "filter": [],
                       "indexRefName": "kibanaSavedObjectMeta.searchSourceJSON.index"})

def vis(id_, title, vis_type, aggs, params, query=""):
    return {
        "id": id_, "type": "visualization", "coreMigrationVersion": "8.8.0",
        "attributes": {
            "title": title, "description": "", "uiStateJSON": "{}", "version": 1,
            "visState": json.dumps({"title": title, "type": vis_type, "aggs": aggs, "params": params}),
            "kibanaSavedObjectMeta": {"searchSourceJSON": search_source(query)},
        },
        "references": [{"name": "kibanaSavedObjectMeta.searchSourceJSON.index", "type": "index-pattern", "id": DV}],
    }

def terms(id_, field, schema="segment", size=10, missing=False):
    return {"id": id_, "enabled": True, "type": "terms", "schema": schema,
            "params": {"field": field, "size": size, "order": "desc", "orderBy": "1",
                       "missingBucket": missing, "missingBucketLabel": "(không có)",
                       "otherBucket": False, "otherBucketLabel": "Khác"}}

COUNT = {"id": "1", "enabled": True, "type": "count", "schema": "metric", "params": {}}
PIE = {"type": "pie", "addTooltip": True, "legendDisplay": "show", "legendPosition": "right", "isDonut": True,
       "labels": {"show": True, "values": True, "last_level": True, "truncate": 100}, "distinctColors": True}
DATE = {"id": "3", "enabled": True, "type": "date_histogram", "schema": "segment",
        "params": {"field": "@timestamp", "useNormalizedEsInterval": True, "interval": "auto",
                   "drop_partials": False, "min_doc_count": 1, "extended_bounds": {}}}

def xy(vis_type, series_mode, value_title):
    return {
        "type": vis_type, "addTooltip": True, "addLegend": True, "legendPosition": "right",
        "categoryAxes": [{"id": "CategoryAxis-1", "type": "category", "position": "bottom", "show": True,
                          "scale": {"type": "linear"}, "labels": {"show": True, "filter": True, "truncate": 100},
                          "title": {}}],
        "valueAxes": [{"id": "ValueAxis-1", "name": "LeftAxis-1", "type": "value", "position": "left", "show": True,
                       "scale": {"type": "linear", "mode": "normal"},
                       "labels": {"show": True, "rotate": 0, "filter": False, "truncate": 100},
                       "title": {"text": value_title}}],
        "seriesParams": [{"show": True, "type": "line" if vis_type == "line" else "histogram",
                          "mode": series_mode, "data": {"label": value_title, "id": "1"},
                          "valueAxis": "ValueAxis-1", "drawLinesBetweenPoints": True, "lineWidth": 2,
                          "showCircles": True}],
        "grid": {"categoryLines": False}, "times": [], "addTimeMarker": False,
        "thresholdLine": {"show": False, "value": 10, "width": 1, "style": "full", "color": "#E7664C"},
        "labels": {}, "detailedTooltip": True, "palette": {"type": "palette", "name": "default"},
    }

objects = [
    {"id": DV, "type": "index-pattern", "coreMigrationVersion": "8.8.0",
     "attributes": {"title": DV, "timeFieldName": "@timestamp", "name": "Call analysis results"},
     "references": []},
    vis("ca-verdict", "Phân bố verdict", "pie", [COUNT, terms("2", "verdict")], PIE, "result : \"OK\""),
    vis("ca-category", "Phân bố issue category", "pie", [COUNT, terms("2", "issue_category")], PIE,
        "result : \"OK\""),
    vis("ca-confidence", "Phân bố độ tin cậy", "pie", [COUNT, terms("2", "confidence")], PIE, "result : \"OK\""),
    vis("ca-fallback", "Fallback (degraded) theo lý do", "pie",
        [COUNT, terms("2", "fallback_reason", missing=True)], PIE, "result : \"OK\""),
    vis("ca-requests", "Request theo thời gian và kết quả", "histogram",
        [COUNT, DATE, terms("2", "result", schema="group")], xy("histogram", "stacked", "Số request")),
    vis("ca-latency", "Latency P50 / P95 (ms)", "line",
        [{"id": "1", "enabled": True, "type": "percentiles", "schema": "metric",
          "params": {"field": "latency_ms", "percents": [50, 95]}}, DATE],
        xy("line", "normal", "Latency (ms)")),
    vis("ca-errors", "Lỗi theo loại", "pie", [COUNT, terms("2", "error_kind")], PIE, "result : \"ERROR\""),
    {"id": "ca-search", "type": "search", "coreMigrationVersion": "8.8.0",
     "attributes": {"title": "Kết quả phân tích gần nhất",
                    "columns": ["request_id", "channel", "result", "verdict", "issue_category", "confidence",
                                "degraded", "fallback_reason", "latency_ms", "model"],
                    "sort": [["@timestamp", "desc"]],
                    "kibanaSavedObjectMeta": {"searchSourceJSON": search_source()}},
     "references": [{"name": "kibanaSavedObjectMeta.searchSourceJSON.index", "type": "index-pattern", "id": DV}]},
]

panels, refs = [], []
layout = [("ca-verdict", 0, 0), ("ca-category", 16, 0), ("ca-confidence", 32, 0),
          ("ca-fallback", 0, 15), ("ca-latency", 16, 15), ("ca-requests", 32, 15),
          ("ca-errors", 0, 30)]
for i, (obj_id, x, y) in enumerate(layout, start=1):
    panels.append({"version": "8.15.0", "type": "visualization", "panelIndex": str(i),
                   "gridData": {"x": x, "y": y, "w": 16, "h": 15, "i": str(i)},
                   "embeddableConfig": {}, "panelRefName": f"panel_{i}"})
    refs.append({"name": f"panel_{i}", "type": "visualization", "id": obj_id})
i = len(layout) + 1
panels.append({"version": "8.15.0", "type": "search", "panelIndex": str(i),
               "gridData": {"x": 16, "y": 30, "w": 32, "h": 15, "i": str(i)},
               "embeddableConfig": {}, "panelRefName": f"panel_{i}"})
refs.append({"name": f"panel_{i}", "type": "search", "id": "ca-search"})

objects.append({
    "id": "ca-dashboard", "type": "dashboard", "coreMigrationVersion": "8.8.0",
    "attributes": {
        "title": "Call Analysis - kết quả phân tích",
        "description": "Sprint 3 T9/T11: phân bố verdict, UNKNOWN rate, fallback rate, latency, lỗi "
                       "(index call-analysis-results, đã sanitize).",
        "panelsJSON": json.dumps(panels), "optionsJSON": json.dumps({"useMargins": True, "hidePanelTitles": False}),
        "timeRestore": True, "timeFrom": "now-7d", "timeTo": "now", "refreshInterval": {"pause": True, "value": 0},
        "kibanaSavedObjectMeta": {"searchSourceJSON": json.dumps({"query": {"query": "", "language": "kuery"},
                                                                  "filter": []})},
    },
    "references": refs,
})

os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w", encoding="utf-8", newline="\n") as f:
    for o in objects:
        f.write(json.dumps(o, ensure_ascii=False) + "\n")
print("wrote", len(objects), "objects")
