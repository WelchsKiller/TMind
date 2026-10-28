package com.nest.tmind.api;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** /today stars: 정수·소수·문자열이 섞여도 파싱이 깨지지 않게 한다. */
final class StarsListAdapter extends TypeAdapter<List<Double>> {

    @Override
    public void write(JsonWriter out, List<Double> value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }
        out.beginArray();
        for (Double v : value) {
            out.value(v != null ? v : 1.0);
        }
        out.endArray();
    }

    @Override
    public List<Double> read(JsonReader in) throws IOException {
        List<Double> stars = new ArrayList<>(7);
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return stars;
        }
        if (in.peek() != JsonToken.BEGIN_ARRAY) {
            in.skipValue();
            return stars;
        }
        in.beginArray();
        while (in.hasNext()) {
            stars.add(readOne(in));
        }
        in.endArray();
        return stars;
    }

    private static double readOne(JsonReader in) throws IOException {
        JsonToken token = in.peek();
        if (token == JsonToken.NULL) {
            in.nextNull();
            return 1.0;
        }
        if (token == JsonToken.NUMBER) {
            return in.nextDouble();
        }
        if (token == JsonToken.STRING) {
            try {
                return Double.parseDouble(in.nextString().trim());
            } catch (Exception ignored) {
                return 1.0;
            }
        }
        in.skipValue();
        return 1.0;
    }
}
