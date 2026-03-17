import json
from datetime import datetime

class Kestra:
    def __init__(self):
        pass

    @staticmethod
    def _send(map):
        print("::" + json.dumps(map) + "::")

    @staticmethod
    def _metrics(name, type, value, tags=None):
        Kestra._send({
            "metrics": [
                {
                    "name": name,
                    "type": type,
                    "value": value,
                    "tags": tags or {}
                }
            ]
        })

    @staticmethod
    def _convert_booleans(obj):
        if isinstance(obj, bool):
            return str(obj)
        elif isinstance(obj, dict):
            return {k: Kestra._convert_booleans(v) for k, v in obj.items()}
        elif isinstance(obj, list):
            return [Kestra._convert_booleans(item) for item in obj]
        return obj

    @staticmethod
    def outputs(map):
        Kestra._send({
            "outputs": Kestra._convert_booleans(map)
        })

    @staticmethod
    def counter(name, value, tags=None):
        Kestra._metrics(name, "counter", value, tags)

    @staticmethod
    def timer(name, duration, tags=None):
        if callable(duration):
            start = datetime.now()
            duration()
            Kestra._metrics(name, "timer", (datetime.now().microsecond - start.microsecond) / 1000, tags)
        else:
            Kestra._metrics(name, "timer", duration, tags);
