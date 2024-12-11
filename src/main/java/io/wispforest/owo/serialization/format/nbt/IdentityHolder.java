package io.wispforest.owo.serialization.format.nbt;

record IdentityHolder<T>(T t) {
    @Override
    public boolean equals(Object obj) {
        if (obj == null || obj.getClass() != this.getClass()) return false;
        return this.t == ((IdentityHolder<?>) obj).t;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this.t);
    }

    @Override
    public String toString() {
        return "IdentityHolder[t=" + t + ']';
    }
}
