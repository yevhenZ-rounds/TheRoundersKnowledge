---
name: ph-cta-button
description: Replace cta_button_shape with proper shape
version: 1.0.0
autoTrigger: false
projectTypes: [android, kotlin]
---

## Instructions
1. Adapt every cta_button_shape.xml reference (for light and dark) with the following model (adapt color if necessary and update corners):

```
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="@color/ph_cta_color" />
    <corners
        android:bottomLeftRadius="12dp"
        android:bottomRightRadius="12dp"
        android:topLeftRadius="12dp"
        android:topRightRadius="12dp" />
</shape>
```